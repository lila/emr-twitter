A = load ‘s3://bucket/twitter.data';                                                    
B = group A by $0 PARALLEL 27;                                              
C = foreach B generate group, COUNT($1);                                    
D = group C by $1 PARALLEL 27;                                              
E = foreach D generate COUNT($1), group; 
