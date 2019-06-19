package edu.vanderbilt.accre.spark_ttree;

import static org.junit.Assert.assertEquals;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TTreeDataSourceIntegrationTest {
    private static SparkSession spark;

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("hadoop.home.dir", "/");
        spark = SparkSession.builder()
                .master("local[*]")
                .appName("test").getOrCreate();
    }

//    @Test
//    public void testLoadDataFrame() {
//        Dataset<Row> df = spark
//                .read()
//                .format("edu.vanderbilt.accre.laurelin.Root")
//                .option("tree",  "tree")
//                .load("testdata/uproot-small-flat-tree.root");
//        df = df.select("Float32", "ArrayFloat32", "SliceFloat32");
//        df.show();
//        // assertEquals(100, df.count());
//    }

//    @Test
//    public void testLoadNestedDataFrame() {
//        Dataset<Row> df = spark
//                .read()
//                .format("edu.vanderbilt.accre.laurelin.Root")
//                .option("tree",  "three/tree")
//                .load("testdata/uproot-nesteddirs.root");
//        df.printSchema();
//        // scalars
//        df.select("evt.I16","evt.I32","evt.I64","evt.F32","evt.F64").show();
//        // fixed arrays
//        df.select("evt.ArrayI16[10]","evt.ArrayI32[10]","evt.ArrayI64[10]","evt.ArrayF32[10]","evt.ArrayF64[10]").show();
//    }

    @Test
    public void testLoadNestedDataFrame() {
        Dataset<Row> df = spark
                .read()
                .format("edu.vanderbilt.accre.laurelin.Root")
                .option("tree",  "Events")
                .load("testdata/all-types.root");
        df.printSchema();
        df.select("ScalarI8", "ScalarI16", "ScalarI32", "ScalarI64").show();
        df.select("ArrayI8", "ArrayI16", "ArrayI32", "ArrayI64").show();
        df.select("SliceI32").show();
        // array form is incorrect, slice form bombs
        df.select("ScalarI1", "ScalarUI1", "ArrayI1", "ArrayUI1").show();
        // following code bombs
        // df.select("SliceI8", "SliceI16", "SliceI64").show();
    }

    @Test
    public void testShortName() {
        Dataset<Row> df = spark
                .read()
                .format("root")
                .option("tree",  "Events")
                .load("testdata/all-types.root");
        df.printSchema();
        df.select("ScalarI8").show();
    }

    @Test
    public void testTwoFiles() {
        Dataset<Row> df = spark
                .read()
                .format("root")
                .option("tree",  "Events")
                .load("testdata/all-types.root", "testdata/all-types.root");
        assertEquals(18, df.count());
    }

    @AfterClass
    public static void afterClass() {
        if (spark != null) {
            spark.stop();
        }
    }

}
