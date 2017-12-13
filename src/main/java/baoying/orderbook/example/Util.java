package baoying.orderbook.example;

public class Util {

    static String toCsvString(long[] array){

        StringBuilder csv = new StringBuilder();
        for(long v : array){
            csv.append(v).append(",");
        }

        if(csv.charAt(csv.length()-1) == ','){
            csv.deleteCharAt(csv.length()-1);
        }

        //TODO format the csv  based on standard https://tools.ietf.org/html/rfc4180
        return csv.toString();
    }
}
