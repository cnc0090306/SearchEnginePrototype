/**
 * Created by xinnacai on 2/16/16.
 */
public class RetrievalModelBM25 extends RetrievalModel {
    public double k_1;
    public double b;
    public double k_3;


    public String defaultQrySopName (){return new String("#sum");}

    public RetrievalModelBM25(double k_1, double b, double k_3){
        this.k_1 = k_1;
        this.b = b;
        this.k_3 = k_3;
    }

    public void setK_1(double k_1){
        this.k_1 = k_1;
    }
    public void setB(double b){
        this.b = b;
    }
    public void setK_3(double k_3){
        this.k_3 = k_3;
    }
    public double getK_1(){
        return this.k_1;
    }
    public double getB(){
        return this.b;
    }
    public double getK_3(){
        return this.k_3;
    }

}
