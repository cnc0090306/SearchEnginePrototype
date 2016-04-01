import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by xinnacai on 3/13/16.
 */
public class QrySopWand extends QrySop {

    public ArrayList<Double> weightArray = new ArrayList<Double>();
    public double sumOfWeight;

//    public QrySopWand(ArrayList<Double> weightArray, double sumOfWeight){
//        this.weightArray.addAll(weightArray);
//        this.sumOfWeight = sumOfWeight;
//    }

    public void setWeightArray(ArrayList<Double> weightArray){
        this.weightArray.addAll(weightArray);
    }

    public void setSumOfWeight(double sumOfWeight){
        this.sumOfWeight = sumOfWeight;
    }



    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    public double getDefaultScore(RetrievalModel r, int docid) throws IOException{
        double score = 1.0;
        int size = weightArray.size();
        for(int i =0; i<this.args.size();i++){
            Qry q_i = this.args.get(i);
            double weight = this.weightArray.get(size-1-i);
            score *=Math.pow(((QrySop)q_i).getDefaultScore(r,docid),weight/this.sumOfWeight);
        }
        return score;
    }

    public double getScore(RetrievalModel r) throws IOException{
        if(!(r instanceof RetrievalModelIndri)){
            throw new IllegalArgumentException("Wand should be applied in Indri");
        }
        double score = 1.0;
        int doc_id = this.docIteratorGetMatch();
        int size = weightArray.size();
        for(int i =0;i < this.args.size();i++){
            Qry q_i = this.args.get(i);
            double weight = weightArray.get(size-1-i);
            if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == doc_id){
                score *= Math.pow(((QrySop)q_i).getScore(r),weight/this.sumOfWeight);
            } else {
                score *=  Math.pow(((QrySop)q_i).getDefaultScore(r,doc_id),weight/this.sumOfWeight);
            }
        }
        return score;
    }

}