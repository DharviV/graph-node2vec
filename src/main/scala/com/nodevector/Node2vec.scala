package com.nodevector


import java.io.Serializable
import scala.util.Try
import scala.collection.mutable.ArrayBuffer
import org.slf4j.{Logger, LoggerFactory}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.{EdgeTriplet, Graph, _}
import com.nodevector.graph.{GraphOps, EdgeAttr, NodeAttr}

object Node2vec extends Serializable {
  lazy val logger: Logger = LoggerFactory.getLogger(getClass.getName);

  var context: SparkContext = null
  var config: Main.Params = null
  var node2id: RDD[(String, Long)] = null
  var indexedEdges: RDD[Edge[EdgeAttr]] = _
  
  
  var indexedNodes: RDD[(VertexId, NodeAttr)] = _
  var graph: Graph[NodeAttr, EdgeAttr] = _
  var randomWalkPaths: RDD[(Long, ArrayBuffer[Long])] = null

  def setup(context: SparkContext, param: Main.Params) = {
    this.context = context
    this.config = param

    this
  }
 
  
   def load(): this.type = {
    val bcMaxDegree = context.broadcast(config.degree)
    val bcEdgeCreator = config.directed match {
      case true => context.broadcast(GraphOps.createDirectedEdge)
      case false => context.broadcast(GraphOps.createUndirectedEdge)
    }

    val inputTriplets: RDD[(Long, Long, Double)] = config.indexed match {
      case true => readIndexedGraph(config.input)
      case false => indexingGraph(config.input)
    }

    val indexedNodes = inputTriplets.flatMap { case (srcId, dstId, weight) =>
      bcEdgeCreator.value.apply(srcId, dstId, weight)
    }.reduceByKey(_++_).map { case (nodeId, neighbors: Array[(VertexId, Double)]) =>
      var neighbors_ = neighbors
      if (neighbors_.length > bcMaxDegree.value) {
        neighbors_ = neighbors.sortWith{ case (left, right) => left._2 > right._2 }.slice(0, bcMaxDegree.value)
      }

      (nodeId, NodeAttr(neighbors = neighbors_.distinct))
    }.coalesce(200, true).cache

    indexedEdges = indexedNodes.flatMap { case (srcId, clickNode) =>
      clickNode.neighbors.map { case (dstId, weight) =>
          Edge(srcId, dstId, EdgeAttr())
      }
    }.coalesce(200,true).cache

    GraphOps.initTransitionProb(indexedNodes, indexedEdges)
  }
   
   
   
 def randomWalk(): this.type = {
    val edge2attr = graph.triplets.map { edgeTriplet =>
      (s"${edgeTriplet.srcId}${edgeTriplet.dstId}", edgeTriplet.attr)
    }.partitionBy(new HashPartitioner(200))
    
    
    edge2attr.first

    for (iter <- 0 until config.numWalks) {
      var prevWalk: RDD[(Long, ArrayBuffer[Long])] = null
      
      var randomWalk = graph.vertices.map { case (nodeId, clickNode) =>
        val pathBuffer = new ArrayBuffer[Long]()
        pathBuffer.append(clickNode.path:_*)
        (nodeId, pathBuffer)
      }.cache
      var activeWalks = randomWalk.first
      graph.unpersist(blocking = false)
      graph.edges.unpersist(blocking = false)
      for (walkCount <- 0 until config.walkLength) {
        prevWalk = randomWalk
        randomWalk = randomWalk.map { case (srcNodeId, pathBuffer) =>
          val prevNodeId = pathBuffer(pathBuffer.length - 2)
          val currentNodeId = pathBuffer.last

          (s"$prevNodeId$currentNodeId", (srcNodeId, pathBuffer))
        }.join(edge2attr).map { case (edge, ((srcNodeId, pathBuffer), attr)) =>
          try {
            val nextNodeIndex = GraphOps.drawAlias(attr.J, attr.q)
            val nextNodeId = attr.dstNeighbors(nextNodeIndex)
            pathBuffer.append(nextNodeId)

            (srcNodeId, pathBuffer)
          } catch {
            case e: Exception => throw new RuntimeException(e.getMessage)
          }
        }.filter(_!=null).cache

      }

        if (randomWalkPaths != null) {
        val prevRandomWalkPaths = randomWalkPaths
        randomWalkPaths = randomWalkPaths.union(randomWalk).cache()
        randomWalkPaths.first
        prevRandomWalkPaths.unpersist(blocking = false)
      } else {
        randomWalkPaths = randomWalk
      }
    }

    this
  }
 
 
 
  def embedding(): this.type = {
    val randomPaths = randomWalkPaths.map { case (vertexId, pathBuffer) =>
      Try(pathBuffer.map(_.toString).toIterable).getOrElse(null)
    }.filter(_!=null)

    Word2vec.setup(context, config).fit(randomPaths)

    this
  }

  def save(): this.type = {
    this.saveRandomPath()
        .saveModel()
        .saveVectors()
  }

  
  def saveRandomPath(randomPaths:RDD[String]): this.type = {
    randomWalkPaths
            .map { case (vertexId, pathBuffer) =>
              Try(pathBuffer.mkString("\t")).getOrElse(null)
            }
            .filter(x => x != null && x.replaceAll("\\s", "").length > 0)
            .repartition(200)
            .saveAsTextFile(config.output)

    this
  }

  def saveModel(): this.type = {
    Word2vec.save(config.output)

    this
  }

  def saveVectors(): this.type = {
    val node2vector = context.parallelize(Word2vec.getVectors.toList)
            .map { case (nodeId, vector) =>
              (nodeId.toLong, vector.mkString(","))
            }

    if (this.node2id != null) {
      val id2Node = this.node2id.map{ case (strNode, index) =>
        (index, strNode)
      }
      
      
      node2vector.join(id2Node)
              .map { case (nodeId, (vector, name)) => s"$name\t$vector" }
              .coalesce(200,false)
              .saveAsTextFile(s"${config.output}.emb")
    } else {
      node2vector.map { case (nodeId, vector) => s"$nodeId\t$vector" }
              .coalesce(200,false)
              .saveAsTextFile(s"${config.output}.emb")
    }

    this
  }
  
  
  
  def loadNode2Id(node2idPath: String)= {
    try {
      this.node2id = context.textFile(config.nodePath).map { node2index =>
        val Array(strNode, index) = node2index.split("\\s")
        (strNode, index.toLong)
      }
    } catch {
      case e: Exception => logger.info("Failed to read node2index file.")
      this.node2id = null
    }

    this
  }

  
   def readIndexedGraph(tripletPath: String) = {
    val bcWeighted = context.broadcast(config.weighted)

    val rawTriplets = context.textFile(tripletPath)
    if (config.nodePath == null) {
      this.node2id = createNode2Id(rawTriplets.map { triplet =>
        val parts = triplet.split("\\s")
        (parts.head, parts(1), -1)
      })
    } else {
      loadNode2Id(config.nodePath)
    }

    rawTriplets.map { triplet =>
      val parts = triplet.split("\\s")
      val weight = bcWeighted.value match {
        case true => Try(parts.last.toDouble).getOrElse(1.0)
        case false => 1.0
      }

      (parts.head.toLong, parts(1).toLong, weight)
    }
  }
   
   
     def indexingGraph(rawTripletPath: String): RDD[(Long, Long, Double)] = {
    val rawEdges = context.textFile(rawTripletPath).map { triplet =>
      val parts = triplet.split("\\s")

      Try {
        (parts.head, parts(1), Try(parts.last.toDouble).getOrElse(1.0))
      }.getOrElse(null)
    }.filter(_!=null)

    this.node2id = createNode2Id(rawEdges)

    rawEdges.map { case (src, dst, weight) =>
      (src, (dst, weight))
    }.join(node2id).map { case (src, (edge: (String, Double), srcIndex: Long)) =>
      try {
        val (dst: String, weight: Double) = edge
        (dst, (srcIndex, weight))
      } catch {
        case e: Exception => null
      }
    }.filter(_!=null).join(node2id).map { case (dst, (edge: (Long, Double), dstIndex: Long)) =>
      try {
        val (srcIndex, weight) = edge
        (srcIndex, dstIndex, weight)
      } catch {
        case e: Exception => null
      }
    }.filter(_!=null)
  }
     
     
      val createUndirectedEdge = (srcId: Long, dstId: Long, weight: Double) => {
        Array(
                (srcId, Array((dstId, weight))), (dstId, Array((srcId, weight)))
                          )}

  val createDirectedEdge = (srcId: Long, dstId: Long, weight: Double) => {
                  Array((srcId, Array((dstId, weight)))
                              )}
     
     
       def createNode2Id[T <: Any](triplets: RDD[(String, String, T)]) = triplets.flatMap { case (src, dst, weight) =>
    Try(Array(src, dst)).getOrElse(Array.empty[String])
  }.distinct().zipWithIndex()

}

