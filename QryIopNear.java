/**
 * Created by xinnacai on 1/31/16.
 */

import java.io.IOException;
import java.util.*;


/**
 * The Near operator for all retrieval models.  The Near operator stores
 * information about max distance between words.
 */
public class QryIopNear extends QryIop {

    /**
     * This query operator has a parameter indicating the maximum distance
     * between words.
     */
    private int distance;

    /**
     * constructor with distance
     *
     * @param distance the maximum distance
     */


    public QryIopNear (int distance) {
        this.distance = distance;    // Default field if none is specified.
    }

    /**
     * Find next common document id
     * @return next minimum document id
     */
    private int findNextDocid(){
        boolean b = true;
        int next_docid = this.args.get(0).docIteratorGetMatch();
        int last = this.args.size()-1;
        while(b) {
            for (Qry arg : this.args) {
                arg.docIteratorAdvanceTo(next_docid);
                if (!arg.docIteratorHasMatch(null)) return Qry.INVALID_DOCID;
                int cur_docid = arg.docIteratorGetMatch();
                if (cur_docid == Qry.INVALID_DOCID) return Qry.INVALID_DOCID;
                if (cur_docid != next_docid) {
                    next_docid = cur_docid;
                    break;
                }
                if(cur_docid == next_docid && arg == this.getArg(last)) b = false;
            }

        }
        return next_docid;

    }

    /**
     * find positions of term2 when term 2 is within n distance of term 1
     * @param p1 positions of term1
     * @param p2 positions of term2
     * @param n  distance n
     * @return positions of term2 when term2 is within the n proximity of term1
     */
    private List<Integer> combinePosition(List<Integer> p1, List<Integer> p2, int n) {
        Collections.sort(p1);
        Collections.sort(p2);
        List<Integer> result = new ArrayList<Integer>();
        int i = 0, j = 0;
        while (i < p1.size() && j < p2.size()) {
            int distance = p2.get(j) - p1.get(i);
            if (distance > 0 && distance <= n) {
                result.add(p2.get(j));
                i++;
                j++;
            } else if (distance < 0) {
                j++;
            } else {
                i++;
            }
        }
        return result;
    }

    /**
     * Evaluate the query operator; the result is an internal inverted
     * list that may be accessed via the internal iterators.
     * @throws IOException Error accessing the Lucene index.
     */

    protected void evaluate () throws IOException {
        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) return;

//        if (!this.getArg(0).docIteratorHasMatch(null)) return;
        while(true) {
            int com_docid = findNextDocid();
//            System.out.println(com_docid);
            if(com_docid == Qry.INVALID_DOCID) break;
            List<Integer> positions = new ArrayList<Integer>();
            List<Integer> loc = this.getArg(0).docIteratorGetMatchPosting().positions;
            Qry p1 = this.getArg(0);
            Qry p2 = p1;
            List<Integer> pos_p1 = ((QryIop) p1).docIteratorGetMatchPosting().positions;
            for (int i = 1; i < args.size(); i++) {
                p2 = this.getArg(i);
                List<Integer> pos_p2 = ((QryIop) p2).docIteratorGetMatchPosting().positions;
                loc = combinePosition(pos_p1, pos_p2, distance);
                p1 = p2;
                pos_p1 = loc;
            }
            if (loc.size() > 0) {
                positions.addAll(loc);
                this.invertedList.appendPosting(com_docid, positions);
            }
            this.getArg(0).docIteratorAdvancePast(com_docid);
            if(!this.getArg(0).docIteratorHasMatch(null)) break;
        }
        double df = this.invertedList.df;
        double docNum = Idx.getDocCount(this.field);
        double pre_idf = Math.log((docNum-df+0.5)/(df+0.5));
        this.idf = Math.max(0,pre_idf);// to eliminate the case that idf will be negative when df > docNum/2
        this.avg_docLen = (double)Idx.getSumOfFieldLengths(this.field)/Idx.getDocCount(this.field);
    }

}


