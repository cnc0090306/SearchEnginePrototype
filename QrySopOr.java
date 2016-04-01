/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
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
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    int max_score = (int) 0.0;
   if(this.docIteratorHasMatch(r)){
     int docid = this.docIteratorGetMatch();
     for(int i =0; i<this.args.size(); i++){
       Qry q_i = this.args.get(i);
       if(q_i instanceof QrySopScore) {
         if(q_i.getArg(0).docIteratorHasMatch(r) && q_i.getArg(0).docIteratorGetMatch() == docid){
           int temp_score = (int)((QrySopScore) q_i).getScore(r);
           max_score = Math.max(max_score,temp_score);
         } else {
           continue;
         }
       }
       if(q_i instanceof QrySopOr){
         if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid){
           int temp_score = (int)((QrySopOr) q_i).getScore(r);
           max_score = Math.max(max_score,temp_score);
         } else {
           continue;
         }
       }
       if(q_i instanceof QrySopAnd) {
         if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid){
           int temp_score = (int)((QrySopAnd) q_i).getScore(r);
           max_score = Math.max(max_score,temp_score);
         } else {
           continue;
         }
       }
     }
   }
    return max_score;
  }

  public double getDefaultScore(RetrievalModel r, int docid){
    return 0.0;
  }

}



