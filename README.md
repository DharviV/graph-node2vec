# graph-node2vec
A repository for the implementation of node2vec!

This repository is based on and is an extension of the implementation of the node2vec algorithm (on Spark) by the creators
of the algorithm as outlined in the following paper:

> node2vec: Scalable Feature Learning for Networks.
> Aditya Grover and Jure Leskovec.
> Knowledge Discovery and Data Mining, 2016.
> 

### Building the project

```
$ git clone <url>
$ mvn clean package
```

Running the aforementioned commands creates the jar file in the following location: "graph-node2vec/target"

####  Input format for generating random walks

The repository consists of a sample input file, inputnodes.txt. which can be used for a test run.

Generally, the format for the input file should be of the following form:

     node1_id_int 	node2_id_int 	<weight_float, optional>
	   or
     node1_str 	node2_str 	<weight_float, optional>, Please set the option "indexed" to false
     
#### To generate random walks

Running the spark job:

spark-submit --class com.nodevector.Main target/node2vec-0.0.1-SNAPSHOT.jar --cmd randomwalk --p 0.5 --q 0.5 -- input input-file-name 
--output output-file-name

#### Arguments

Following arguments are available for running the Spark job:

```
--input 

--output

--walkLength

--numWalks

--p

--q

```

#### Output format for random walks

The output file consists of x rrandom walks where x = number of nodes * numWalks. The format is as follows:

    src_node_id_int 	node1_id_int 	node2_id_int 	... 	noden_id_int


#### Running node2vec 

Running the spark job:

spark-submit --class com.nodevector.Main target/node2vec-0.0.1-SNAPSHOT.jar --cmd node2vec --p 0.5 --q 0.5 -- input input-file-name 
--output output-file-name

#### Output format for node2vec

    node1_str 	dim1 dim2 ... dimd
    
Here, dim1 dim2 ....dimd are tab separated files which denote the d-dimensional vector representation of node2vec

### References:

1. [node2vec: Scalable Feature Learning for Networks](http://arxiv.org/abs/1607.00653)
