# SearchEnginePrototype
This is an application that reads queries from a file, evaluates them against an index, and writes the results to an 
output file.  This class contains the main method, a method for reading parameter and query files, initialization methods,
a simple query parser, a simple query processor, and methods for reporting results.

The {@link Qry} hierarchy implements query evaluation using a document at a time' (DaaT) methodology.  Initially it contains an
#OR operator for the unranked Boolean retrieval model, #AND operator for Boolean and Indri retrieval model, #SUM operator for BM25 
Retrieval Modela. #SYN (synonym), #NEAR and #WIN(window) operators for any retrieval model. It is easily extended 
to support additional query operators and retrieval models. 

The {@link RetrievalModel} hierarchy stores parameters and information required by different retrieval models.  Retrieval
models that need these parameters (e.g., BM25 and Indri) use them very frequently, so the RetrievalModel class emphasizes fast access.

 The {@link Idx} hierarchy provides access to information in the Lucene index.  It is intended to be simpler than accessing the
 Lucene index directly. As the search engine becomes more complex, it becomes useful to have a standard approach to representing 
 documents and scores. The {@link ScoreList} class provides this capability.
