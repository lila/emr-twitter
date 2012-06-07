// TwitterEMR
//
// java application to find all pairs of users to follow each other
// in the twitter dataset


package com.amazon.karanb.emr;

package com.amazon.karanb.emr;

import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import java.util.HashSet;

public class TwitterEMR {

    static Logger log = Logger.getLogger(TwitterEMR.class);

    public static class TwitterMapper
            extends Mapper<LongWritable, Text, LongWritable, LongWritable> {

        Logger log = Logger.getLogger(this.getClass());

        /*
         * TextInputFormat passed in the line number as a Long and the entire row as a Text
         */
        public void map(LongWritable n1, Text n2, Context context) throws IOException, InterruptedException {

                Long n1l, n2l;
                String line = n2.toString();
                // Split the input into columns. Split on any whitespace
                String[] line_parts = line.split("\\s+");

                n1l = Long.valueOf( line_parts[0] );
                n2l = Long.valueOf( line_parts[1] );

                context.write(new LongWritable(n1l), new LongWritable(n2l));
                // Swap the values and multiply the first value by -1 to indicate
                // that this "follow" is going in the opposite direction
                context.write(new LongWritable(n2l),  new LongWritable(n1l * -1));
        }
    }

    public static class TwitterReducer extends Reducer<LongWritable, LongWritable, LongWritable, LongWritable> {
        public void reduce(LongWritable key, Iterable<LongWritable> values, Context context)
                throws InterruptedException, IOException {

            // We'll use a HashSet to hold the followers for a given account
            HashSet<Long> myHash = new HashSet();
            long l;

            for (LongWritable t : values) {
                l = t.get();
                myHash.add( l );
                // If the hash contains the negative value, we found our paired follower
                if ( l < 0 && myHash.contains( l * -1) )
                        context.write(key, new LongWritable(l * -1));
            }
        }
    }


    private static String[] loadConfiguration(Configuration conf, String[] args)
            throws IOException
    {

        return loadConfiguration(conf, null, args);

    }

    private static String[] loadConfiguration(Configuration conf, String configurationFileName, String[] args)
            throws IOException
    {
        /*
        * first load the configuration from the build properties (typically packaged in the jar)
        */
        System.out.println("loading application.properties ...");
        try {
            Properties buildProperties = new Properties();
            buildProperties.load(TwitterEMR.class.getResourceAsStream("/application.properties"));
            for (Enumeration e = buildProperties.propertyNames(); e.hasMoreElements(); ) {
                String k = (String) e.nextElement();
                System.out.println("setting " + k + " to " + buildProperties.getProperty(k));
                System.setProperty(k, buildProperties.getProperty(k));
                conf.set(k, buildProperties.getProperty(k));
            }
        } catch (Exception e) {
            System.out.println("unable to find application.properties ... skipping");
        }
        /*
        finally, allow user to override from commandline
         */
        return new GenericOptionsParser(conf, args).getRemainingArgs();
    }


    private static void printConfiguration(Configuration conf, Logger log, String[] allProperties) {

        for (String option : allProperties) {

            if (option.startsWith("---")) {
                log.info(option);
                continue;
            }
            String c = conf.get(option);
            if (c != null) {
                log.info("\toption " + option + ":\t" + c);
            }
        }
    }

    /**
     * starts off the hadoop application
     *
     * @param args input fasta sequence and output location
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        /*
        load the application configuration parameters (from deployment directory)
         */

        Configuration conf = new Configuration();
        String[] otherArgs = loadConfiguration(conf, args);

        /*
        process arguments
         */
        if (otherArgs.length != 2) {
            System.err.println("Usage: TwitterEMR <fasta seq url> <output url>");
            System.exit(2);
        }

        //long sequenceFileLength = 0;

        /*
        seems to help in file i/o performance
         */
        conf.setInt("io.file.buffer.size", 1024 * 1024);
        conf.set("key.value.separator.in.input.line", " ");

        log.info(System.getProperty("application.name") + "[version " + System.getProperty("application.version") + "] starting with following parameters");
        log.info("\tinput file: " + otherArgs[0]);
        log.info("\toutput dir: " + otherArgs[1]);

        String[] allProperties = {
                "--- application properties ---",
                "application.name",
                "application.version",
                "--- system properties ---",
                "mapred.min.split.size",
                "mapred.max.split.size"
        };

        printConfiguration(conf, log, allProperties);

        Job job = new Job(conf, System.getProperty("application.name") + "-" + System.getProperty("application.version") + "-step1");
        job.setJarByClass(TwitterEMR.class);
        job.setMapperClass(TwitterMapper.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(LongWritable.class);
        job.setReducerClass(TwitterReducer.class);
        job.setNumReduceTasks(1); // force there to be only 1 to get a single output file

        FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
        FileOutputFormat.setOutputPath(job, new Path(otherArgs[1] + "/step1"));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}

