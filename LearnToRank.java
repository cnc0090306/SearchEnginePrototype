/**
 * Created by xinnacai on 4/10/16.
 */
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

import org.apache.lucene.index.Term;

public class LearnToRank {
    String trainingQueryFile;
    String trainingQrelsFile;
    String trainingFeatureVectorsFile;
    String pageRankFile;
    String svmRankLearnPath;
    String svmRankClassifyPath;
    String svmRankModelFile;
    String testingFeatureVectorsFile;
    String testingDocumentScores;
    boolean [] feature_able;
    double svmRankParamC = 0.001;
    FileWriter fw;
    FileWriter fwtrain;
    FileWriter fwtest;
    String trecEvalOutputPath;
    String queryFilePath;

    RetrievalModelBM25 BM25;
    RetrievalModelIndri Indri;

    ArrayList<String> trainQueries;
    ArrayList<String> testQueries;
    HashMap<String,ArrayList<Feature>> trainqRel = new HashMap<>();// map qid to a list of Feature
    HashMap<String,ArrayList<Feature>> testqRel = new HashMap<>();
    HashMap<String,ArrayList<Feature>> testScore = new HashMap<>();
    HashMap<Integer,TermVector> tmBody;// map docId to TermVector in body field
    HashMap<Integer,TermVector> tmInlink;
    HashMap<Integer,TermVector> tmUrl;
    HashMap<Integer,TermVector> tmTitle;
    HashMap<Integer,Double> pageRank; // map doc internal id to its page rank score
    HashMap<String, Double> collectionLength;
    HashMap<String,Double> docCount;
    DecimalFormat df = new DecimalFormat("0.0");
    ArrayList<String> query_id;
    static double docNum;


    private class Feature implements Comparable<Feature> {
        String qid;
        double score;
        double[] feature = new double[18];
        String externalId;

        Feature(String qid, double score, double[] feature, String externalId){
            this.qid = qid;
            this.score = score;
            this.externalId = externalId;
            for(int i =0;i <this.feature.length;i++){
                this.feature[i] = feature[i];
            }
        }

        public void setFeature(double[] feature) {
            for (int i = 0; i < this.feature.length; i++) {
                this.feature[i] = feature[i];
            }
        }
         public int compareTo(Feature f2){
            return -Double.compare(this.score,f2.score);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(score+" qid:"+qid);
            for(int i =0;i<this.feature.length;i++){
                int n = i+1;
                sb.append(" "+n+":"+this.feature[i]);
            }
            sb.append(" # "+this.externalId+"\n");
            return sb.toString();
        }

    }

    public LearnToRank(Map<String, String> p) throws Exception {
        checkParameter(p);
        readParameter(p);
        docNum = Idx.getNumDocs();
        tmBody = new HashMap<>();
        tmUrl = new HashMap<>();
        tmTitle = new HashMap<>();
        tmInlink = new HashMap<>();
        collectionLength = new HashMap<>();
        double clbody = (double)Idx.getSumOfFieldLengths("body");
        collectionLength.put("body", clbody);
        double cltitle = (double)Idx.getSumOfFieldLengths("title");
        collectionLength.put("title", cltitle);
        double clinlink = (double)Idx.getSumOfFieldLengths("inlink");
        collectionLength.put("inlink", clinlink);
        double clurl = (double)Idx.getSumOfFieldLengths("url");
        collectionLength.put("url", clurl);
        docCount = new HashMap<>();
        double dcbody = (double)Idx.getDocCount("body");
        docCount.put("body",dcbody);
        double dctitle = (double)Idx.getDocCount("title");
        docCount.put("title",dctitle);
        double dcurl = (double)Idx.getDocCount("url");
        docCount.put("url",dcurl);
        double dcinlink = (double)Idx.getDocCount("inlink");
        docCount.put("inlink",dcinlink);
        readParameter(p);
        readPageRank();
        fw = new FileWriter(trecEvalOutputPath);
        generateTrainData();
        trainProcess();
        generateTestData();
        testProcess();
        readScore();
        outputResult();
        fw.close();
    }

    public void generateTrainData() throws IOException {
        try {
            trainQueries = readQueries(trainingQueryFile);
            fwtrain = new FileWriter(trainingFeatureVectorsFile);
            readQrels(trainingQrelsFile);
            for (String s : trainQueries) {
                int d = s.indexOf(':');
                String qid = s.substring(0, d);
                String query = s.substring(d + 1);
                String[] stems = QryEval.tokenizeQuery(query);
                try {
                    generateFeature(qid, stems, trainqRel);
                } catch (Exception e) {

                }
                normalize(qid, trainqRel);
                writeTrainFeatureFile(qid);
            }
        } finally{
            fwtrain.close();
        }
    }

    public void generateTestData() throws IOException {
        try {
            testQueries = readQueries(queryFilePath);
            query_id = new ArrayList<String>();
            fwtest = new FileWriter(testingFeatureVectorsFile);
            for (String s : testQueries) {
                int d = s.indexOf(':');
                String qid = s.substring(0, d);
                query_id.add(qid);
                String query = s.substring(d + 1);
                generateTestRel(qid, query);
                String[] stems = QryEval.tokenizeQuery(query);
                try {
                    generateFeature(qid, stems, testqRel);
                } catch (Exception e) {

                }
                normalize(qid, testqRel);
                writeTestFeatureFile(qid);
            }
        }finally{
            fwtest.close();
        }
    }

    public ArrayList<String> readQueries(String queryFile){
        ArrayList<String> result = new ArrayList<>();
        File file = new File(queryFile);
        try {
            Scanner scanner = new Scanner(file);
            String qLine;
            while (scanner.hasNextLine()) {
                qLine = scanner.nextLine().trim();
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }
                result.add(qLine);
            }
            scanner.close();
        }catch(FileNotFoundException e){
            System.out.println(e.toString());
        }
        return result;
    }

    public void readQrels(String fileName) throws FileNotFoundException{
        File file = new File(fileName);
        Scanner scanner = new Scanner(file);
        String line;
        String pre_qid="";
        String qid;
        double score;
        double[] feature = new double[18];
        String externalId;
        ArrayList<Feature> flist = new ArrayList<>();
        if(scanner.hasNextLine()){
            flist = new ArrayList<>();
            line = scanner.nextLine();
            String[] str = line.split(" ");
            qid = str[0];
            pre_qid = qid;
            score = Double.valueOf(str[3]);
            externalId = str[2];
            Feature f = new Feature(qid,score,feature,externalId);
            flist.add(f);
        }
        while(scanner.hasNextLine()){
            line = scanner.nextLine();
            String[] str = line.split(" ");
            qid = str[0];
            score = Double.valueOf(str[3]);
            externalId = str[2];
            Feature f = new Feature(qid,score,feature,externalId);
            if(!qid.equals(pre_qid)){
                trainqRel.put(pre_qid,flist);
                flist = new ArrayList<>();
                flist.add(f);
                pre_qid = qid;
            } else{
                flist.add(f);
            }
        }
        scanner.close();
        trainqRel.put(pre_qid,flist);
    }

    public void readPageRank() throws Exception{
        File file = new File(pageRankFile);
        pageRank = new HashMap<>();
        try{
            Scanner scanner = new Scanner(file);
            String line;
            while(scanner.hasNextLine()){
                line = scanner.nextLine();
                String[] str = line.split("\t");
                if(!Idx.isExistDoc(str[0])) {
                    continue;
                }
                int docId = Idx.getInternalDocid(str[0]);
                double score = Double.parseDouble(str[1]);
                pageRank.put(docId,score);
            }
        } catch(FileNotFoundException e){
            System.out.println(e.toString());
        }
    }
    public void generateFeature(String qid, String[] stems, HashMap<String,ArrayList<Feature>> qD) throws Exception {
        int length = stems.length;
        ArrayList<Feature> flist = qD.get(qid);
        ArrayList<Feature> new_flist = new ArrayList<Feature>();
        for(Feature fr : flist) {
            double[] f = new double[18];
            for (int i = 0; i < f.length; i++) {
                f[i] = -Double.MAX_VALUE;
            }
            if (!Idx.isExistDoc(fr.externalId)) {
                continue;
            }
            int docId = Idx.getInternalDocid(fr.externalId);
            if (!tmBody.containsKey(docId)) {
                TermVector bd = new TermVector(docId,"body");
                tmBody.put(docId, bd);
            }
            if (!tmTitle.containsKey(docId)) {
                TermVector tt = new TermVector(docId,"title");
                tmTitle.put(docId, tt);
            }
            if (!tmUrl.containsKey(docId)) {
                TermVector url = new TermVector(docId,"url");
                tmUrl.put(docId, url);
            }
            if (!tmInlink.containsKey(docId)) {
                TermVector il = new TermVector(docId,"inlink");
                tmInlink.put(docId, il);
            }
            if (feature_able[0]) {
                f[0] = Double.parseDouble(Idx.getAttribute("score", docId));
            }
//            System.out.println("spam score "+f[0]);
            String url = Idx.getAttribute("rawUrl", docId);
            if (feature_able[1]) {
                int num = 0;
                for (int i = 0; i < url.length(); i++) {
                    if (url.charAt(i) == '/') num++;
                }
                f[1] = (double) num;
            }
//            System.out.println("url depth"+f[1]);
            if (feature_able[2] && url.contains("wikipedia.org")) {
                f[2] = 1.0;
            } else if (feature_able[2] && !url.contains("wikipedia.org")) {
                f[2] = 0.0;
            }
//            System.out.println("wiki"+f[2]);
            if (feature_able[3] && pageRank.containsKey(docId)) {
                f[3] = pageRank.get(docId);
            }
//            System.out.println("page rank "+f[3]);
            TermVector body_tm = tmBody.get(docId);
            if (feature_able[4]) {
                if (body_tm.positionsLength() != 0) {
                    f[4] = featureBM25(stems, body_tm,"body");
                }
            }
//            System.out.println("bm25 body: "+ f[4]);
            if (feature_able[5]) {
                if (body_tm.positionsLength() != 0) {
                    f[5] = featureIndri(stems, body_tm, "body");
                }
            }
//            System.out.println("indri body:"+ f[5]);
            if (feature_able[6]) {
                if (body_tm.positionsLength() != 0) {
                    f[6] = featureTermOverlap(stems, body_tm);
                }
            }
            if (feature_able[16]) {
                if (body_tm.positionsLength() != 0) {
                    f[16] = featureVSM(stems, body_tm,"body");
                }
            }
//            System.out.println("term overlap "+f[6]);
            TermVector title_tm = tmTitle.get(docId);
            if (feature_able[7]) {
                if (title_tm.positionsLength() != 0){
                    f[7] = featureBM25(stems, title_tm, "title");
                }
            }
            if (feature_able[8]) {
                if (title_tm.positionsLength() != 0){
                    f[8] = featureIndri(stems, title_tm, "title");
                }
            }
            if (feature_able[9]) {
                if (title_tm.positionsLength() != 0) {
                    f[9] = featureTermOverlap(stems, title_tm);
                }
            }
            if (feature_able[17]) {
                if (title_tm.positionsLength() != 0) {
                    f[17] = featureVSM(stems, title_tm,"title");
                }
            }
            TermVector url_tm = tmUrl.get(docId);
            if (feature_able[10]) {
                if (url_tm.positionsLength() != 0) {
                    f[10] = featureBM25(stems, url_tm,"url");
                }
            }
            if (feature_able[11]) {
                if (url_tm.positionsLength() != 0) {
                    f[11] = featureIndri(stems, url_tm,"url");
                }
            }
            if (feature_able[12]) {
                if (url_tm.positionsLength() != 0){
                    f[12] = featureTermOverlap(stems, url_tm);
                }
            }

            TermVector inlink_tm = tmInlink.get(docId);
            if (feature_able[13]) {
                if (inlink_tm.positionsLength() != 0) {
                    f[13] = featureBM25(stems, inlink_tm,"inlink");
                }
            }
            if (feature_able[14]) {
                if (inlink_tm.positionsLength() != 0) {
                    f[14] = featureIndri(stems, inlink_tm,"inlink");
                }
            }
            if (feature_able[15]) {
                if (inlink_tm.positionsLength() != 0) {
                    f[15] = featureTermOverlap(stems, inlink_tm);
                }
            }
            Feature newFeature = new Feature(qid, fr.score, f, fr.externalId);
            new_flist.add(newFeature);
        }
        qD.put(qid,new_flist);
    }

    public void normalize(String qid, HashMap<String,ArrayList<Feature>> qD) {
        System.out.println(qD.size());
        ArrayList<Feature> flist = qD.get(qid);
        Maxmin maxmin = findMaxMin(flist);
        double[] max =maxmin.max;
        double[] min =maxmin.min;
        for(Feature f: flist){
            for(int i =0;i<f.feature.length;i++){
                if(f.feature[i] == -Double.MAX_VALUE) {
                    f.feature[i] = 0;
                } else if(max[i] == min[i]) {
                    f.feature[i] =0;
                } else {
                    f.feature[i] = (f.feature[i] - min[i])/(max[i] - min[i]);
                }
            }
        }
        qD.put(qid,flist);
    }

    public Maxmin findMaxMin(ArrayList<Feature> flist) {
        double[]max = new double[18];
        double[]min = new double[18];
        for(int i=0;i<max.length;i++){
            max[i] = -Double.MAX_VALUE;
            min[i] = Double.MAX_VALUE;
        }
        for(Feature f : flist){
            int length = f.feature.length;
            for(int i =0;i<length;i++){
                if(f.feature[i] > max[i] && f.feature[i]!=-Double.MAX_VALUE) max[i] = f.feature[i];
                if(f.feature[i] < min[i] && f.feature[i]!=-Double.MAX_VALUE) min[i] = f.feature[i];
            }
        }
        for(int i =0;i <max.length;i++){
            if(max[i] == -Double.MAX_VALUE) max[i]=0.0;
            if(min[i] == Double.MAX_VALUE) min[i]=0.0;
        }
//        for(int i =0;i<16;i++) System.out.print(max[i]);
//        System.out.println();
//        for(int i =0;i<16;i++) System.out.print(min[i]);
        Maxmin maxmin = new Maxmin(max,min);
        return maxmin;
    }

    private class Maxmin {
        double[] min;
        double[] max;
        Maxmin(double[] max, double[] min){
            this.max = max;
            this.min = min;
        }
    }

    public void writeTrainFeatureFile(String qid) throws IOException{
        try{
            ArrayList<Feature> flist = trainqRel.get(qid);
            for(Feature f: flist){
                fwtrain.write(f.toString());
            }
        }catch(Exception e){
            System.out.println(e.toString());
        }
    }

    public void writeTestFeatureFile(String qid) throws IOException{
        try{
            ArrayList<Feature> flist = testqRel.get(qid);
            for(Feature f: flist){
                fwtest.write(f.toString());
            }
        }catch(Exception e){
            System.out.println(e.toString());
        }
    }

    public void outputResult() throws IOException{
            for(int i=0;i<query_id.size();i++){
                ArrayList<Feature> flist = testqRel.get(query_id.get(i));
                Collections.sort(flist);
                int n =1;
                for(Feature f:flist){
                    String s = f.qid + " Q0 " + f.externalId + " " + (n++) + " "
                            + f.score + " run-1" + "\n";
                    fw.write(s);
                }
            }
    }

    public void readScore() {
        File file = new File(testingDocumentScores);
        try {
            Scanner scanner = new Scanner(file);
            String line;
            for(String qid: query_id){
                ArrayList<Feature> flist = testqRel.get(qid);
                for(int i =0;i<flist.size();i++){
                    flist.get(i).score = Double.parseDouble(scanner.nextLine().trim());
                }
                testqRel.put(qid,flist);
            }
            scanner.close();
        } catch(FileNotFoundException e){
            System.out.println("can not find file");
        }
    }

    public void generateTestRel(String qid, String query) throws IOException{
        ScoreList r = QryEval.processQuery(qid,query,BM25);
        ArrayList<Feature> flist = new ArrayList<Feature>();
        double[] features = new double[18];
        for(int i =0;i<r.size();i++){
            Feature f = new Feature(qid,0,features,Idx.getExternalDocid(r.getDocid(i)));
            flist.add(f);
        }
        testqRel.put(qid,flist);
    }

    public void trainProcess() throws IOException{
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { svmRankLearnPath, "-c", String.valueOf(svmRankParamC), trainingFeatureVectorsFile,
                        svmRankModelFile});
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        try{
            int retValue = cmdProc.waitFor();
            if (retValue != 0) {
                throw new Exception("SVM Rank crashed.");
            }
        } catch (Exception e){
            System.out.println(e.toString());
        }
    }

    public void testProcess() throws IOException{
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { svmRankClassifyPath,testingFeatureVectorsFile,svmRankModelFile,
                        testingDocumentScores});
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        try{
            int retValue = cmdProc.waitFor();
            if (retValue != 0) {
                throw new Exception("SVM Learn crashed.");
            }
        } catch (Exception e){
            System.out.println(e.toString());
        }
    }

    public double featureBM25(String[] stems,TermVector tm,String field) throws IOException{
        List<String> stemlist = Arrays.asList(stems);
        double score = 0.0;
        for(int i =1;i<tm.stemsLength();i++){
            String term = tm.stemString(i);
            if(!stemlist.contains(term)){
                continue;
            }
            int index = tm.indexOfStem(term);
            double df = (double)tm.stemDf(index);
            double pre_idf = Math.log((docNum-df+0.5)/(df+0.5));
            double rsj_weight = Math.max(0,pre_idf);
            double tf = (double)tm.stemFreq(index);
            double k_1 = BM25.getK_1();
            double b = BM25.getB();
            double k_3 = BM25.getK_3();
            double avg_docLen = (double)collectionLength.get(field)/docCount.get(field);
            double docLen = (double)tm.positionsLength();
            double tf_weight = (double)tf/(tf+k_1*((1-b)+b*(docLen/avg_docLen)));
            double user_weight = (k_3+1)*1/(k_3+1);
            double s = rsj_weight * tf_weight * user_weight;
            score+= s;
        }
        return score;
    }

    public double featureIndri(String[] stems,TermVector tm,String field) throws IOException{
        double score = 1.0;
        List<String> stemlist = Arrays.asList(stems);
        Map<String,Integer> map = new HashMap<String,Integer>();
        int yes =0;
        for(int i =1;i<tm.stemsLength();i++) {
            String term = tm.stemString(i);
            if (stemlist.contains(term)) {
                yes++;
                map.put(term,i);
            }
        }
        if(yes == 0) return 0.0;
        for(String stem : stems){
            double lambda = Indri.getLambda();
            double mu = Indri.getMu();
            double tf;
            double ctf;
            int index;
            if(!map.containsKey(stem)) {
                tf = 0.0;
                Term term = new Term(stem,field);
                ctf = Idx.INDEXREADER.totalTermFreq(term);
            } else{
                index = map.get(stem);
                tf = (double)tm.stemFreq(index);
                ctf = tm.totalStemFreq(index);
            }
            double collectionLen = (double)collectionLength.get(field);
            double docLen = (double)tm.positionsLength();
//            double mle_qc = (double)ctf/collectionLen;
            double s = (double)(1-lambda)*((tf+(mu*(double)ctf/collectionLen))/(docLen+mu)) + (lambda * (double)ctf/collectionLen);
            score *= Math.pow(s,1.0/stems.length);
//            double score = (1-lambda)*((mu*mle_qc)/(docLen+mu)) + (lambda * mle_qc);
//            score *=Math.pow(temp_score,1.0/q_size);
        }
        return score;
    }

    public double featureTermOverlap(String[] stems, TermVector tm){
        double score;
        List<String> stemlist = Arrays.asList(stems);
        int yes =0;
        for(int i =1;i<tm.stemsLength();i++) {
            String term = tm.stemString(i);
            if (stemlist.contains(term)) {
                yes++;
            }
        }
        score = (double)yes/stems.length;
        return score;
    }

    public double featureVSM(String[] stems,TermVector tm,String field) throws IOException{
        List<String> stemlist = Arrays.asList(stems);
        Map<String,Integer> map = new HashMap<>();
        double score = 0.0;
        double docVector_len = 0.0;
        double qVector_len = 0.0;
        for(int i =1;i<tm.stemsLength();i++) {
            String term = tm.stemString(i);
            double ss = Math.pow(Math.log(tm.stemFreq(i))+1,2);
            docVector_len = ss + docVector_len;
            if (!stemlist.contains(term)) {
                continue;
            } else {
                if (map.containsKey(term)) {
                    int freq = map.get(term) + 1;
                    map.put(term, freq);
                } else {
                    map.put(term, 1);
                }
            }
        }
        docVector_len = Math.sqrt(docVector_len);
        if(map.size() == 0) return score;
        for(String term : map.keySet()){
            int index = tm.indexOfStem(term);
            double df = (double)tm.stemDf(index);
            double idf = Math.log(docNum/df);
            double qs = Math.pow((Math.log(map.get(term))+1)*idf,2);
            qVector_len = qs + qVector_len;
            double tf = (double)tm.stemFreq(index);
            double avg_docLen = (double)collectionLength.get(field)/docCount.get(field);
            double docLen = (double)tm.positionsLength();
            double tf_weight = Math.log(tf)+1;
            double user_weight = Math.log(map.get(term))+1;
            double s = (double)tf_weight *(user_weight * idf);
            score+= s;
        }
        qVector_len = Math.sqrt(qVector_len);
        score = (double) score /(qVector_len * docVector_len);
        return score;
    }

    private void checkParameter(Map<String,String> p){
        if(!(p.containsKey("letor:trainingQueryFile") &&
        p.containsKey("letor:trainingQrelsFile") &&
        p.containsKey("letor:trainingFeatureVectorsFile") &&
        p.containsKey("letor:pageRankFile") &&
        p.containsKey("letor:svmRankLearnPath") &&
        p.containsKey("letor:svmRankClassifyPath") &&
        p.containsKey("letor:svmRankModelFile") &&
        p.containsKey("letor:testingFeatureVectorsFile") &&
        p.containsKey("letor:testingDocumentScores") &&
        p.containsKey("BM25:k_1") &&
        p.containsKey("BM25:b") &&
        p.containsKey("BM25:k_3") &&
        p.containsKey("Indri:mu") &&
        p.containsKey("Indri:lambda"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }
    }

    private void readParameter(Map<String,String> p) throws Exception {
        trainingQueryFile = p.get("letor:trainingQueryFile");
        trainingQrelsFile = p.get("letor:trainingQrelsFile");
        trainingFeatureVectorsFile = p.get("letor:trainingFeatureVectorsFile");
        pageRankFile = p.get("letor:pageRankFile");
        svmRankLearnPath = p.get("letor:svmRankLearnPath");
        svmRankClassifyPath = p.get("letor:svmRankClassifyPath");
        svmRankModelFile = p.get("letor:svmRankModelFile");
        testingFeatureVectorsFile = p.get("letor:testingFeatureVectorsFile");
        testingDocumentScores = p.get("letor:testingDocumentScores");
        trecEvalOutputPath = p.get("trecEvalOutputPath");
        queryFilePath = p.get("queryFilePath");
        double k_1 = Double.valueOf(p.get("BM25:k_1"));
        double b = Double.valueOf(p.get("BM25:b"));
        double k_3 = Double.valueOf(p.get("BM25:k_3"));
        if (k_1 < 0.0 || k_3 < 0.0) throw new IllegalArgumentException("required: k_1>=0.0, k_3>=0.0");
        if (b < 0.0 && b > 1.0) throw new IllegalArgumentException("required:  0<=b<=1");
        BM25 = new RetrievalModelBM25(k_1, b, k_3);
        double lambda = Double.valueOf(p.get("Indri:lambda"));
        double mu = Double.valueOf(p.get("Indri:mu"));
        if (mu < 0) throw new IllegalArgumentException("required: mu>=0");
        if (lambda < 0.0 && lambda > 1.0) throw new IllegalArgumentException("required:  0<=lambda<=1");
        Indri = new RetrievalModelIndri(mu, lambda);
        feature_able = new boolean[18];
        for(int i =0;i<18;i++){
            feature_able[i] = true;
        }

        if(p.containsKey("letor:featureDisable")) {
            String[] f = p.get("letor:featureDisable").trim().split(",");
            for(String str : f){
                int n = Integer.parseInt(str);
                feature_able[n-1] = false;
            }
        }

        if(p.containsKey("letor:svmRankParamC")) {
            svmRankParamC = Double.parseDouble(p.get("letor:svmRankParamC"));
        }

    }

}
