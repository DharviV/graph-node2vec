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

####  Input

The repository consists of a sample input file, inputnodes.txt. which can be used for a test run.

Generally, the format for the input file should be of the following form:

     node1_id_int 	node2_id_int 	<weight_float, optional>
	   or
     node1_str 	node2_str 	<weight_float, optional>, Please set the option "indexed" to false

### References:

1. [node2vec: Scalable Feature Learning for Networks](http://arxiv.org/abs/1607.00653)
