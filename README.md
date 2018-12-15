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
     
#### Procedure to generate random walks

1. After cloning the repo and building the code using - mvn clean package, a  jar file is created in the target/ folder.
2. To simulate a random path, the following spark job is run:

       spark-submit --class com.nodevector.Main target/node2vec-0.0.1-SNAPSHOT.jar --cmd randomwalk --p 1 --q 1 --walkLength 20 --input inputnodes.txt --output outputwalks

where the following values were passed: p=1, q=1, walkLength=20, input file name = inputnodes.txt (sample input file in the repo), output folder name = outputwalks (some name of new output folder created)

3. The following command is used to copy the output folder from HDFS: hadoop fs -copyToLocal outputwalks .
Part files are created in the output folder where the part files consist of some random walks. The number of part files created depends on the number of partitions.

Example output: After running the aforementioned spark job, random walks were generated. Output file name "part-00001" was seen to consist of random walks generated from nodes 12, 5, 23, 21, 17, 29, 25, 14, 30, 34, 14, 27, where each random walk was of length 20.

#### Arguments

Following arguments are available for running the Spark job:

```
--input 

--output

--walkLength

--numWalks

--p

--q

--degree (maximum number of neighbours, default is set to 30)

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
