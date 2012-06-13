A = load ‘s3://data.karanb.amazon.com/social/twitter.net';                                                    
B = group A by $0 PARALLEL 54;                                              
C = foreach B generate group, COUNT($1);                                    
D = group C by $1 PARALLEL 54;                                              
E = foreach D generate group, COUNT($1); 
F = order E by $0 asc PARALLEL 54;
dump F;
