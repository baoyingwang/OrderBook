package baoying.orderbook.example;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.PKIXRevocationChecker;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Util {
    private final static Logger log = LoggerFactory.getLogger(Util.class);

    static String toCsvString(long[] array){

        return toCsvString(array, 0, array.length);

    }


    static String toCsvString(long[] array, int start, int endExclusive){

        StringBuilder csv = new StringBuilder();
        for(int i = start; i<endExclusive; i++){
            csv.append(array[i]).append(",");
        }

        if(csv.charAt(csv.length()-1) == ','){
            csv.deleteCharAt(csv.length()-1);
        }

        //TODO format the csv  based on standard https://tools.ietf.org/html/rfc4180
        return csv.toString();
    }
}
