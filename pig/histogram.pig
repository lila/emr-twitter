A = load ’s3://bucket/twitter.data';                                                 
B = foreach A generate FLATTEN(TOBAG(TOTUPLE($0, $1), TOTUPLE($1,$0)));
C = group B by $0 PARALLEL 100;                                             
D = foreach C generate group, COUNT($1);                                    
store D into 's3://bucket/twitter.out’
