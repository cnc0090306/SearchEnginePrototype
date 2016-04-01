/**
 * Created by xinnacai on 1/31/16.
 */
import java.io.*;
public class QrySopAnd extends QrySop{

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if(r instanceof RetrievalModelIndri) return this.docIteratorHasMatchMin(r);
        else return this.docIteratorHasMatchAll(r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean (r);
        } else if(r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else if(r instanceof RetrievalModelIndri){
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException{
        if(this.docIteratorHasMatch(r)){
            return 0.0;
        }
        else {
            return 1.0;
        }
    }

     private double getScoreRankedBoolean (RetrievalModel r) throws IOException{
         double min_score = Integer.MAX_VALUE;
         if(!this.docIteratorHasMatch(r)) return 0.0;
         int docid = this.docIteratorGetMatch();
         for(Qry q_i:this.args) {
             if (q_i instanceof QrySopScore) {
                 double temp_score = ((QrySopScore) q_i).getScore(r);
                 min_score = Math.min(min_score,temp_score);
             }
             if(q_i instanceof QrySopOr) {
                 double temp_score = ((QrySopOr) q_i).getScore(r);
                 min_score = Math.min(min_score, temp_score);
             }
             if (q_i instanceof QrySopAnd) {
                 double temp_score = ((QrySopAnd) q_i).getScore(r);
                 min_score = Math.min(min_score, temp_score);
             }
         }
         return min_score;

     }

    public double getDefaultScore(RetrievalModel r, int docid) throws IOException{
        double score = 1.0;
        if(r instanceof RetrievalModelIndri){
            double q_size = this.args.size();
            for(Qry q_i :this.args) {
                double temp_score = ((QrySop) q_i).getDefaultScore(r, docid);
                score *= Math.pow(temp_score, 1.0 / q_size);
            }
        }

        return score;
    }


    public double getScoreIndri(RetrievalModel r) throws IOException{
        double score = 1.0;
        double q_size = this.args.size();
        int docid = this.docIteratorGetMatch();
        for(Qry q_i : this.args){
            if( q_i.docIteratorHasMatch(r)  && q_i.docIteratorGetMatch() == docid){
                double temp_score = ((QrySop)q_i).getScore(r);
                score *=Math.pow(temp_score,1.0/q_size);
            } else{
                double temp_score = ((QrySop)q_i).getDefaultScore(r,docid);
                score *=Math.pow(temp_score,1.0/q_size);
            }
        }
        return score;
    }

}
