/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.1.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final EnglishAnalyzerConfigurable ANALYZER =
            new EnglishAnalyzerConfigurable(Version.LUCENE_43);
    private static final String[] TEXT_FIELDS =
            {"body", "title", "url", "inlink", "keywords"};

    private static FileWriter writer;
    private static FileWriter fw;
    private static boolean fb = false;
    private static int fbDocs = 0;
    private static int fbTerms = 0;
    private static int fbMu = -1;
    private static double fbOrigWeight = -1;
    private static String fbInitialRankingFile = "";
    private static String fbExpansionQueryFile = "";
    private static Map<String, ScoreList> initialRanking = new HashMap<String, ScoreList>();

    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.


        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        Map<String, String> parameters = readParameterFile(args[0]);

        //  Configure Fuery lexical processing to match index lexical
        //  processing.  Initialize the index and retrieval model.

        ANALYZER.setLowercase(true);
        ANALYZER.setStopwordRemoval(true);
        ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

        Idx.initialize(parameters.get("indexPath"));
        RetrievalModel model = initializeRetrievalModel(parameters);

        writer = new FileWriter(parameters.get("trecEvalOutputPath"));

        if (fb && !fbInitialRankingFile.equals("")) {
            readDocumentRanking(fbInitialRankingFile);
        }
        processQueryFile(parameters.get("queryFilePath"), model);

        //  Clean up.

        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if (modelString.equals("bm25")) {
            double k_1 = Double.valueOf(parameters.get("BM25:k_1"));
            double b = Double.valueOf(parameters.get("BM25:b"));
            double k_3 = Double.valueOf(parameters.get("BM25:k_3"));
            if (k_1 < 0.0 || k_3 < 0.0) throw new IllegalArgumentException("required: k_1>=0.0, k_3>=0.0");
            if (b < 0.0 && b > 1.0) throw new IllegalArgumentException("required:  0<=b<=1");
            model = new RetrievalModelBM25(k_1, b, k_3);
        } else if (modelString.equals("indri")) {
            double lambda = Double.valueOf(parameters.get("Indri:lambda"));
            double mu = Double.valueOf(parameters.get("Indri:mu"));
            if (mu < 0) throw new IllegalArgumentException("required: mu>=0");
            if (lambda < 0.0 && lambda > 1.0) throw new IllegalArgumentException("required:  0<=lambda<=1");
            model = new RetrievalModelIndri(mu, lambda);
        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * Optimize the query by removing degenerate nodes produced during
     * query parsing, for example '#NEAR/1 (of the)' which turns into
     * '#NEAR/1 ()' after stopwords are removed; and unnecessary nodes
     * or subtrees, such as #AND (#AND (a)), which can be replaced by 'a'.
     */
    static Qry optimizeQuery(Qry q) {

        //  Term operators don't benefit from optimization.

        if (q instanceof QryIopTerm) {
            return q;
        }

        //  Optimization is a depth-first task, so recurse on query
        //  arguments.  This is done in reverse to simplify deleting
        //  query arguments that become null.

        for (int i = q.args.size() - 1; i >= 0; i--) {

            Qry q_i_before = q.args.get(i);
            Qry q_i_after = optimizeQuery(q_i_before);

            if (q_i_after == null) {
                q.removeArg(i);            // optimization deleted the argument
            } else {
                if (q_i_before != q_i_after) {
                    q.args.set(i, q_i_after);    // optimization changed the argument
                }
            }
        }

        //  If the operator now has no arguments, it is deleted.

        if (q.args.size() == 0) {
            return null;
        }

        //  Only SCORE operators can have a single argument.  Other
        //  query operators that have just one argument are deleted.

        if ((q.args.size() == 1) &&
                (!(q instanceof QrySopScore))) {
            q = q.args.get(0);
        }

        return q;

    }

    ;

    /**
     * Return a query tree that corresponds to the query.
     *
     * @param qString A string containing a query.
     * @param model   A retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    static Qry parseQuery(String qString, RetrievalModel model) throws IOException {

        //  Add a default query operator to every query. This is a tiny
        //  bit of inefficiency, but it allows other code to assume
        //  that the query will return document ids and scores.

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";

        //  Simple query tokenization.  Terms like "near-death" are handled later.

        StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
        String token = null;

        //  This is a simple, stack-based parser.  These variables record
        //  the parser's state.

        Qry currentOp = null;
        Stack<Qry> opStack = new Stack<Qry>();
        LinkedList<Double> weightQueue = new LinkedList<Double>();
        boolean isWeight = false;


        //  Each pass of the loop processes one token. The query operator
        //  on the top of the opStack is also stored in currentOp to
        //  make the code more readable.

        while (tokens.hasMoreTokens()) {

            token = tokens.nextToken();
            //System.out.println(token);

            if (token.matches("[ ,(\t\n\r]")) {
                continue;
            } else if (token.equals(")")) {    // Finish current query op.

                // If the current query operator is not an argument to another
                // query operator (i.e., the opStack is empty when the current
                // query operator is removed), we're done (assuming correct
                // syntax - see below).
                if (currentOp instanceof QrySopWand || currentOp instanceof QrySopWsum) {
                    ArrayList<Double> weightArray = new ArrayList<>();
                    double sumOfWeight = 0.0;
                    for (int i = 0; i < currentOp.args.size(); i++) {
                        double weight = weightQueue.removeLast();
                        sumOfWeight += weight;
                        weightArray.add(weight);
                    }
                    if (currentOp instanceof QrySopWand) {
                        ((QrySopWand) currentOp).setWeightArray(weightArray);
                        ((QrySopWand) currentOp).setSumOfWeight(sumOfWeight);
                    }
                    if (currentOp instanceof QrySopWsum) {
                        ((QrySopWsum) currentOp).setWeightArray(weightArray);
                        ((QrySopWsum) currentOp).setSumOfWeight(sumOfWeight);
                    }
                }

                opStack.pop();

                if (opStack.empty())
                    break;

                // Not done yet.  Add the current operator as an argument to
                // the higher-level operator, and shift processing back to the
                // higher-level operator.
                Qry arg = currentOp;
                currentOp = opStack.peek();
                currentOp.appendArg(arg);
                if (currentOp instanceof QrySopWand || currentOp instanceof QrySopWsum)
                    isWeight = true;

            } else if (token.equalsIgnoreCase("#or")) {
                currentOp = new QrySopOr();
                currentOp.setDisplayName(token);
                opStack.push(currentOp);
            } else if (token.equalsIgnoreCase("#and")) {
                currentOp = new QrySopAnd();
                currentOp.setDisplayName(token);
                opStack.push(currentOp);
            } else if (token.equalsIgnoreCase("#sum")) {
                currentOp = new QrySopSum();
                currentOp.setDisplayName(token);
                opStack.push(currentOp);
            } else if (token.split("/").length > 1) {
                String[] parts = token.split("/");
                int n = Integer.parseInt(token.trim().split("/")[1]);
                if (parts[0].equalsIgnoreCase("#near")) {
                    currentOp = new QryIopNear(n);
                }
                if (parts[0].equalsIgnoreCase("#window")) {
                    currentOp = new QryIopWin(n);
                }
                currentOp.setDisplayName(token);
                opStack.push(currentOp);
            } else if (token.equalsIgnoreCase("#syn")) {
                currentOp = new QryIopSyn();
                currentOp.setDisplayName(token);
                opStack.push(currentOp);
            } else if (token.equalsIgnoreCase("#wand")) {
                currentOp = new QrySopWand();
                currentOp.setDisplayName(token);
                opStack.push(currentOp);
                isWeight = true;
            } else if (token.equalsIgnoreCase("#wsum")) {
                currentOp = new QrySopWsum();
                currentOp.setDisplayName(token);
                opStack.push(currentOp);
                isWeight = true;
            } else {
                if ((currentOp instanceof QrySopWand || currentOp instanceof QrySopWsum) && isWeight == true) {
                    try {
                        double weight = Double.parseDouble(token);
                        weightQueue.addLast(weight);
                        isWeight = false;
                        continue;
                    } catch (NumberFormatException e) {
                        throw new NumberFormatException("This number should be double");
                    }
                }


                //  Split the token into a term and a field.

                int delimiter = token.indexOf('.');
                String field = null;
                String term = null;

                if (delimiter < 0) {
                    field = "body";
                    term = token;
                } else {
                    field = token.substring(delimiter + 1).toLowerCase();
                    term = token.substring(0, delimiter);
                }
                if ((field.compareTo("url") != 0) &&
                        (field.compareTo("keywords") != 0) &&
                        (field.compareTo("title") != 0) &&
                        (field.compareTo("body") != 0) &&
                        (field.compareTo("inlink") != 0)) {
                    throw new IllegalArgumentException("Error: Unknown field " + token);
                }


                //  Lexical processing, stopwords, stemming.  A loop is used
                //  just in case a term (e.g., "near-death") gets tokenized into
                //  multiple terms (e.g., "near" and "death").
                if (currentOp instanceof QrySopWand || currentOp instanceof QrySopWsum)
                    isWeight = true;
                String t[] = tokenizeQuery(term);


                for (int j = 0; j < t.length; j++) {
                    Qry termOp = new QryIopTerm(t[j], field);
                    currentOp.appendArg(termOp);
                }
                if ((currentOp instanceof QrySopWand || currentOp instanceof QrySopWsum) && t.length == 0)
                    weightQueue.removeLast();
            }
        }


        //  A broken structured query can leave unprocessed tokens on the opStack,

        if (tokens.hasMoreTokens()) {
            throw new IllegalArgumentException
                    ("Error:  Query syntax is incorrect.  " + qString);
        }

        return currentOp;
    }


//
//    public static boolean isDouble(String s){
//        try{
//            Double.parseDouble(s);
//            return true;
//        } catch(NumberFormatException e){
//            return false;
//        }
//    }

    /**
     * Print a message indicating the amount of memory used. The caller
     * can indicate whether garbage collection should be performed,
     * which slows the program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     *
     * @param qString A string that contains a query.
     * @param model   The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qid, String qString, RetrievalModel model)
            throws IOException {

        Qry q = parseQuery(qString, model);
        q = optimizeQuery(q);

        ScoreList r = new ScoreList();

        // Show the query that is evaluated

//    System.out.println("    --> " + q);

        if (fb == false) {
            r = processInitialQuery(q, model);
        } else {
            if (fbInitialRankingFile.equals("")) {
                r = processInitialQuery(q, model);
            } else {
                if (!initialRanking.containsKey(qid)) {
                    throw new IOException("initial ranking file does not include this qid");
                }
                r = initialRanking.get(qid);
            }
            String defaultOp = model.defaultQrySopName();
            qString = defaultOp + "(" + qString + ")";
            String expandedQuery = expand(r);
            if(!(fbExpansionQueryFile.equals(""))) {
                printExpandedQuery(qid,expandedQuery);
            }
            String newQuery = "#wand (" + String.valueOf(fbOrigWeight) + " " + qString + " " +
                    String.valueOf(1 - fbOrigWeight) + " " + expandedQuery + ")";
            Qry new_q = parseQuery(newQuery, model);
            new_q = optimizeQuery(new_q);
            r = processInitialQuery(new_q, model);
        }
        return r;
    }

    /**
     * process query without concern of expansion.
     *
     * @param q     parses query
     * @param model
     * @return ScoreList
     * @throws IOException
     */
    static ScoreList processInitialQuery(Qry q, RetrievalModel model) throws IOException {

        if (q != null) {

            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);


                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            r.sort();
            r.truncate(100);
            return r;
        } else
            return null;
    }

    private static String expand( ScoreList r) throws IOException {
        TreeMap<String, ArrayList<Integer>> termDic = new TreeMap<String, ArrayList<Integer>>();
        TreeMap<String, Double> ple = new TreeMap<String, Double>();
        ArrayList<Double> docLenList = new ArrayList<Double>();
        TreeMap<String, Double> scoreMap = new TreeMap<String, Double>();
        double sumLength = Idx.getSumOfFieldLengths("body");

        int docSize = Math.min(fbDocs, r.size());
        for (int i = 0; i < docSize; i++) {
            int doc_id = r.getDocid(i);
            TermVector tv = new TermVector(doc_id, "body");
            double docLen = Idx.getFieldLength("body", doc_id);
            docLenList.add(docLen);
            double docScore = r.getDocidScore(i);
            for (int j = 1; j < tv.stemsLength(); j++) {
                String term = tv.stemString(j);
                if (term.contains(".") || term.contains(",")) {
                    continue;
                }
                if (termDic.containsKey(term)) {
                    ArrayList<Integer> docList = termDic.get(term);
                    docList.add(doc_id);
                    termDic.put(term, docList);
                } else {
                    ArrayList<Integer> docList = new ArrayList<Integer>();
                    docList.add(doc_id);
                    termDic.put(term, docList);
                }
                double mle;
                if (!ple.containsKey(term)) {
                    double ctf = tv.totalStemFreq(j);
                    mle = (double) ctf / sumLength;
                    ple.put(term, mle);
                } else {
                    mle = ple.get(term);
                }
                double tf = (double) tv.stemFreq(j);
                double ptd = (tf + fbMu * mle) / (double)(docLen + fbMu);
                double addScore = ptd * docScore * (Math.log(1.0 / mle));
                if (scoreMap.containsKey(term)) {
                    double score = scoreMap.get(term);
                    score = score + addScore;
                    scoreMap.put(term, score);
                } else {
                    scoreMap.put(term, addScore);
                }
            }
        }

        for(String term : termDic.keySet()){
            ArrayList<Integer> docs = termDic.get(term);
            for(int i =0;i<docSize;i++){
                int doc_id = r.getDocid(i);
                if(docs.contains(doc_id)){
                    continue;
                } else {
                    double docScore = r.getDocidScore(i);
                    double mle = ple.get(term);
                    double docLen = docLenList.get(i);
                    double ptd = (fbMu * mle) /(double)(docLen + fbMu);
                    double addScore = ptd * docScore * (Math.log(1.0/mle));
                    double score = scoreMap.get(term);
                    score = score + addScore;
                    scoreMap.put(term,score);
                }
            }
        }
        LinkedList<Map.Entry<String,Double>> scoreList = new LinkedList<Map.Entry<String,Double>>(scoreMap.entrySet());
        Collections.sort(scoreList,new Comparator<Map.Entry<String,Double>> (){
            @Override
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return(o2.getValue().compareTo(o1.getValue()));
            }
        });
        StringBuilder expandedQuery = new StringBuilder();
        expandedQuery.append("#wand (");
        for(int i =0;i<fbTerms && i <termDic.size();i++){
            double score = scoreList.peek().getValue();
            String term = scoreList.peek().getKey();
            String s = String.format("%.4f",score);
            expandedQuery.append(" "+ s +" "+term);
            scoreList.poll();
        }
        expandedQuery.append(")");
        return expandedQuery.toString();
    }

    /**
     * Process the query file.
     *
     * @param queryFilePath
     * @param model
     * @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(String queryFilePath,
                                 RetrievalModel model)
            throws IOException {
        if(fb && !(fbExpansionQueryFile .equals(""))){
            fw = new FileWriter(fbExpansionQueryFile);
        }

        BufferedReader input = null;

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.

            while ((qLine = input.readLine()) != null) {
                    int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                System.out.println("Query " + qLine);

                ScoreList r = null;

                r = processQuery(qid,query, model);

                if (r != null) {
//                    printResults(qid, r);
//                    System.out.println();
                    outPutTrecEvalResults(qid,r);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
            writer.close();
            if(fb && !(fbExpansionQueryFile .equals(""))){
                fw.close(); 
            }
          

        }
    }

    static void printExpandedQuery(String qid, String expandedQuery) throws IOException {
        fw.write(qid+": "+expandedQuery+"\n");
    }


    /**
     * Print the query results.
     * <p>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName, ScoreList result) throws IOException {

        if (result.size() < 1) {
            System.out.println(queryName+" Q0 dummy 1 0 run-1");
        } else {
            for (int i = 0; i < result.size(); i++) {
                System.out.println(queryName+" Q0 "+Idx.getExternalDocid(result.getDocid(i))+" "+(i+1)+" "+result.getDocidScore(i)+" run-1");
            }
        }
    }

    static void outPutTrecEvalResults(String qid, ScoreList result) throws IOException {
        if(result.size()<1){
            writer.write(qid + " Q0 dummy 1 0 run-1"+"\n");
        } else {
            for (int j = 0; j < result.size(); j++) {
                String print = qid + " Q0 " + Idx.getExternalDocid(result.getDocid(j)) + " " + (j + 1) + " "
                        + result.getDocidScore(j) + " run-1" + "\n";
                writer.write(print);

            }
        }
    }

    /**
     * Read initial document ranking from file.
     * @param fbInitialRanking file name of initial ranking
     * @throws Exception
     */
    static void readDocumentRanking(String fbInitialRanking) throws Exception {
        BufferedReader input = null;
        try{
            input = new BufferedReader(new FileReader(fbInitialRanking));
            String line = null;
            String current_qid = null;
            ScoreList r = new ScoreList();
            while((line = input.readLine())!=null){
                String[] rLine = line.split(" ");
                String qid = rLine[0].trim();
                int docid =  Idx.getInternalDocid(rLine[2].trim());
                double score = Double.parseDouble(rLine[4].trim());
                if(qid.equalsIgnoreCase(current_qid)) {
                    r.add(docid,score);
                } else {
                    if(current_qid !=null){
                        r.sort();
                        initialRanking.put(current_qid,r);
                        r = new ScoreList();
                    }
                    current_qid = qid;
                    r.add(docid,score);
                }
            }
            if(current_qid != null){
                r.sort();
                initialRanking.put(current_qid,r);
            }

        } catch(IOException ex){
            ex.printStackTrace();
        } finally {
            input.close();
        }
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for
     * processing them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();

        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath") &&
                parameters.containsKey("retrievalAlgorithm")||
                (parameters.containsKey("BM25:k_1") &&
                parameters.containsKey("BM25:b") &&
                parameters.containsKey("BM25:k_3"))||(
                parameters.containsKey("Indri:mu") &&
                        parameters.containsKey("Indri:lambda")))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        if(parameters.containsKey("fb") && parameters.get("fb").equalsIgnoreCase("true")) {
            fb = true;
        }
        if(parameters.containsKey("fbDocs")){
            fbDocs = Integer.parseInt(parameters.get("fbDocs"));
        }
        if(parameters.containsKey("fbTerms")){
            fbTerms = Integer.parseInt(parameters.get("fbTerms"));
        }
        if(parameters.containsKey("fbMu")){
            fbMu = Integer.parseInt(parameters.get("fbMu"));
        }
        if(parameters.containsKey("fbOrigWeight")){
            fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
        }
        if(parameters.containsKey("fbInitialRankingFile")){
            fbInitialRankingFile = parameters.get("fbInitialRankingFile");
        }
        if(parameters.containsKey("fbExpansionQueryFile")){
            fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
        }

        return parameters;
    }

    /**
     * Given a query string, returns the terms one at a time with stopwords
     * removed and the terms stemmed using the Krovetz stemmer.
     * <p>
     * Use this method to process raw query terms.
     *
     * @param query String containing query
     * @return Array of query tokens
     * @throws IOException Error accessing the Lucene index.
     */
    static String[] tokenizeQuery(String query) throws IOException {

        TokenStreamComponents comp =
                ANALYZER.createComponents("dummy", new StringReader(query));
        TokenStream tokenStream = comp.getTokenStream();

        CharTermAttribute charTermAttribute =
                tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();

        List<String> tokens = new ArrayList<String>();

        while (tokenStream.incrementToken()) {
            String term = charTermAttribute.toString();
            tokens.add(term);
        }

        return tokens.toArray(new String[tokens.size()]);
    }

}
