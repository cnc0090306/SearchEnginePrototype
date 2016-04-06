/**
 * Created by xinnacai on 3/13/16.
 * @author Xinna Cai
 */
import java.io.IOException;
import java.util.ArrayList;
public class QrySopWsum extends QrySop{
    /** weight vector for different evidence */
    public ArrayList<Double> weightArray = new ArrayList<Double>();
    /** sum of weight vector */
    public double sumOfWeight;

    /**
     * set weight vector.
     * @param weightArray
     */
    public void setWeightArray(ArrayList<Double> weightArray){
        this.weightArray.addAll(weightArray);
    }

    /**
     * set sum of weight.
     * @param sumOfWeight
     */
    public void setSumOfWeight(double sumOfWeight){
        this.sumOfWeight = sumOfWeight;
    }

    /**
     * Indicates whether the query has a match.
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r){
        return this.docIteratorHasMatchMin(r);
    }

    /**
     * get default score for Indri model.
     * @param  r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException{
        double score = 0.0;
        int size = weightArray.size();
        for(int i =0;i<this.args.size();i++){
            double weight = this.weightArray.get(size-1-i);
            Qry q_i = this.args.get(i);
            score += (weight/this.sumOfWeight* (((QrySop) q_i).getDefaultScore(r,docid))) ;
        }
        return score;
    }

    /**
     * getScore for Indri model.
     * @param  r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException{
        if(!(r instanceof RetrievalModelIndri)){
            throw new IllegalArgumentException("Wsum only applies to Indri");
        }
        double score = 0.0;
        int doc_id = this.docIteratorGetMatch();
        int size = weightArray.size();
        for(int i = 0; i < this.args.size();i++){
            Qry q_i = this.args.get(i);
            double weight = this.weightArray.get(size-1-i);
            if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == doc_id){
                score += (weight/this.sumOfWeight* (((QrySop) q_i).getScore(r))) ;
            } else{
                score += (weight/this.sumOfWeight* (((QrySop) q_i).getDefaultScore(r,doc_id))) ;
            }
        }
        return score;
    }
}
