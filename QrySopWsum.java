/**
 * Created by xinnacai on 3/13/16.
 */
import java.io.IOException;
import java.util.ArrayList;
public class QrySopWsum extends QrySop{

    public ArrayList<Double> weightArray = new ArrayList<Double>();
    public double sumOfWeight;

//    public QrySopWsum(ArrayList<Double> weightArray, double sumOfWeight){
//        this.weightArray = weightArray;
//        this.sumOfWeight = sumOfWeight;
//    }

    public void setWeightArray(ArrayList<Double> weightArray){
        this.weightArray.addAll(weightArray);
    }

    public void setSumOfWeight(double sumOfWeight){
        this.sumOfWeight = sumOfWeight;
    }

    public boolean docIteratorHasMatch(RetrievalModel r){
        return this.docIteratorHasMatchMin(r);
    }

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
