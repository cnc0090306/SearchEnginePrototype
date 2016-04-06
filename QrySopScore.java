/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  @author Xinna Cai
 */
import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
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
    } else if(r instanceof RetrievalModelBM25){
      return this.getScoreBM25(r);
    } else if(r instanceof RetrievalModelIndri){
      return this.getScoreIndri(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if(this.args.size()!=1){
      throw new IllegalArgumentException("Score Operator should have only one argument");
    }
    Qry q = this.args.get(0);
    if(!(q instanceof QryIopTerm) && !(q instanceof QryIopNear)){
      throw new IllegalArgumentException("Argument should be QryIopTerm or QryIopNear");
    }
    if(q.docIteratorHasMatch(r)) return 1.0;
    else return 0.0;
  }

  /**
   * getScore for ranked Boolean model.
   * @param  r The retrieval model that determines how scores are calculated.
   * @return The document score.
   * @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
      if (this.args.size()!=1) {
        throw new IllegalArgumentException("Score Operator should have only one argument");
      }
      Qry q = this.args.get(0);
      if (!(q instanceof QryIop)) {
        throw new IllegalArgumentException("Argument should be QryIopTerm or QryIopNear");
      }
        return ((QryIop) q).docIteratorGetMatchPosting().tf;
  }

  /**
   * getScore for BM25 model.
   * @param  r The retrieval model that determines how scores are calculated.
   * @return The document score.
   * @throws IOException Error accessing the Lucene index
   */
  public double getScoreBM25(RetrievalModel r) throws IOException {
    if(this.args.size()!=1){
      throw new IllegalArgumentException("Score Operator should have only one argument");
    }
    Qry q = this.args.get(0);
    if(! (q instanceof QryIop)){
      throw new IllegalArgumentException("Argument should be QryIop");
    }
    double rsj_weight = ((QryIop)q).idf;
    double tf = (double)((QryIop)q).docIteratorGetMatchPosting().tf;
    double k_1 = ((RetrievalModelBM25)r).getK_1();
    double b = ((RetrievalModelBM25)r).getB();
    double k_3 = ((RetrievalModelBM25)r).getK_3();
    double avg_docLen = ((QryIop)q).avg_docLen;
    double docLen = Idx.getFieldLength(((QryIop)q).field,q.docIteratorGetMatch());
    double tf_weight = (double)tf/(tf+k_1*((1-b)+b*(docLen/avg_docLen)));
    double user_weight = (k_3+1)*1/(k_3+1);
    double score = rsj_weight * tf_weight * user_weight;
    return score;

  }

  /**
   * getScore for Indri model.
   * @param  r The retrieval model that determines how scores are calculated.
   * @return The document score.
   * @throws IOException Error accessing the Lucene index
   */
  public double getScoreIndri(RetrievalModel r) throws IOException {
    if(this.args.size()!=1){
      throw new IllegalArgumentException("Score Operator should have only one argument");
    }
    Qry q = this.args.get(0);
    if(! (q instanceof QryIop)){
      throw new IllegalArgumentException("Argument should be QryIop");
    }
    int docid = ((QryIop)q).docIteratorGetMatch();
    double lambda = ((RetrievalModelIndri)r).getLambda();
    double mu = ((RetrievalModelIndri)r).getMu();
    double tf = ((QryIop)q).docIteratorGetMatchPosting().tf;
    double ctf = ((QryIop)q).getCtf();
    double collectionLen = Idx.getSumOfFieldLengths(((QryIop)q).field);
    double docLen = Idx.getFieldLength(((QryIop)q).field,docid);
    double mle_qc = ctf/collectionLen;
    double score = (1-lambda)*((tf+(mu*mle_qc))/(docLen+mu)) + (lambda * mle_qc);
    return score;
  }

  /**
   * get default score for Indri model in which document do not have match terms.
   * @param  r The retrieval model that determines how scores are calculated.
   * @return The document score.
   * @throws IOException Error accessing the Lucene index
   */
  public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
    if(this.args.size()!=1){
      throw new IllegalArgumentException("Score Operator should have only one argument");
    }
    Qry q = this.args.get(0);
    if(! (q instanceof QryIop)){
      throw new IllegalArgumentException("Argument should be QryIop");
    }
    double lambda = ((RetrievalModelIndri)r).getLambda();
    double mu = ((RetrievalModelIndri)r).getMu();
    double ctf = ((QryIop)q).getCtf();
    double collectionLen = (double)Idx.getSumOfFieldLengths(((QryIop)q).field);
    double docLen = (double)Idx.getFieldLength(((QryIop)q).field,docid);
    double mle_qc = ctf/collectionLen;
    double score = (1-lambda)*((mu*mle_qc)/(docLen+mu)) + (lambda * mle_qc);
    return score;
  }

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
