/**
 * Created by xinnacai on 2/17/16.
 */
public class RetrievalModelIndri extends RetrievalModel {
    public double mu;
    public double lambda;

    public String defaultQrySopName(){return new String("#and");}

    public RetrievalModelIndri(double mu, double lambda){
        this.mu = mu;
        this.lambda = lambda;
    }
    public void setMu(double mu){this.mu = mu;}
    public void setLambda(double lambda){this.lambda = lambda;}

    public double getMu(){return this.mu;}
    public double getLambda() {return this.lambda;}
}
