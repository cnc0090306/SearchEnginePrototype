import java.io.IOException;

/**
 * Created by xinnacai on 2/17/16.
 */
public class QrySopSum extends QrySop {

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }


    public double getScore(RetrievalModel r) throws IOException {
        double score = 0.0;
        if (!(r instanceof RetrievalModelBM25)) {
            throw new IllegalArgumentException("this should be applied in BM25 model");
        }
//        if (this.docIteratorHasMatch(r)) {
            int doc_id = this.docIteratorGetMatch();
            for (Qry q_i : this.args) {
                if(q_i instanceof QrySopOr || q_i instanceof QrySopWand || q_i instanceof QrySopWsum){
                    throw new IllegalArgumentException ("BM25 only supports SUM,Near, SYN and WIN Operator");
                }
                if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == doc_id) {
                    score += ((QrySop) q_i).getScore(r);
                }
            }
//        }
        return score;

    }

    public double getDefaultScore(RetrievalModel r, int docid) throws IOException{
        return 0.0;
    }

}
