import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by xinnacai on 3/13/16.
 */
public class QryIopWin extends QryIop{
    private int distance;

    public QryIopWin(int distance) {
        this.distance = distance;
    }

    /**
     * This method finds the next common document id of all arguments
     * @return the common document id
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
     * This class represents the result state of comparing positions of each
     * argument in its own inverted list.
     */
    private class Result {
        int max;
        int min;
        int minIndex;

        Result(){
            max = Integer.MIN_VALUE;
            min =Integer.MAX_VALUE;
            minIndex =0;
        }
    }

    /**
     *  Evaluate the query operator; the result is an internal inverted
     *  list that may be accessed via the internal iterators.
     *  @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate () throws IOException {
        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) return;
        int size = this.args.size();

        while (true) {
            int com_docid = findNextDocid();
//            System.out.println(com_docid);
            if (com_docid == Qry.INVALID_DOCID) break;
            List<Integer> merged_position = new ArrayList<Integer>();
            int[] pointer = new int[size];
            Result res = new Result();
            for (int i = 0; i < size; i++) {
                pointer[i] = 0;
            }
            List<List<Integer>> poslist = new ArrayList<List<Integer>>();
            for (int i = 0; i < size; i++) {
                List<Integer> position = this.getArg(i).docIteratorGetMatchPosting().positions;
                poslist.add(position);
            }
            res = advanceNext(pointer, poslist, size);
            while(res!=null){
                if (res.max - res.min+1 <= this.distance) {
                    merged_position.add(res.min);
                    for(int i=0;i<size;i++){
                        pointer[i]++;
                    }
                    res = advanceNext(pointer,poslist,size);
                } else {
                    pointer[res.minIndex]++;
                    res = advanceNext(pointer,poslist,size);
                }
            }
            if(merged_position.size()>=1){
                this.invertedList.appendPosting(com_docid, merged_position);
            }
            this.getArg(0).docIteratorAdvancePast(com_docid);
            if(!this.getArg(0).docIteratorHasMatch(null)) break;

        }

//        if (!this.getArg(0).docIteratorHasMatch(null)) return;

            double df = this.invertedList.df;
            double docNum = (double)Idx.getDocCount(this.field);
            double pre_idf = Math.log((docNum - df + 0.5) / (df + 0.5));
            this.idf = Math.max(0, pre_idf);// to eliminate the case that idf will be negative when df > docNum/2
            this.avg_docLen = (double) Idx.getSumOfFieldLengths(this.field) / Idx.getDocCount(this.field);

    }

    /**
     * This method compare current positions of each argument in its inverted list
     * @param pointer the state of each current positions of each argument
     * @param poslist it stores all the position list of all arguments
     * @param size the number of arguments
     * @return the result of comparing positions
     */

    private Result advanceNext(int[] pointer,List<List<Integer>> poslist, int size){
        Result res = new Result();
        for(int i =0;i<size;i++){
                List<Integer> position = poslist.get(i);
                if(pointer[i] == position.size()) return null;
                int temp = position.get(pointer[i]);
                if(temp > res.max) res.max = temp;
                if(temp < res.min){
                    res.min = temp;
                    res.minIndex = i;
                }
            }
        return res;
    }
}
