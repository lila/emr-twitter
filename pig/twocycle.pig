A = load ’s3://data.karanb.amazon.com/social/twitter.data';                                                 
B = foreach A generate FLATTEN(TOBAG(TOTUPLE($0, $1), TOTUPLE($1,$0)));
C = group B by $0 PARALLEL 100;                                             
D = foreach C generate group, COUNT($1);                                    
dump D;
