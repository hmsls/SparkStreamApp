package com.bsb.yyjr.StreamDemo1

import kafka.serializer.StringDecoder
import org.apache.log4j.Logger
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}

object StreamDemo {
  val logger = Logger.getLogger("StreamDemo")
  //业务逻辑处理
  def processLines(sqlContext:SQLContext,elem:RDD[String]):Unit={
    if(!elem.isEmpty()){
      val sqlRdd = sqlContext.read.json(elem)
      sqlRdd.printSchema()
      sqlRdd.registerTempTable("StreamDemo_table")

      //业务流量统计
      val req = sqlContext.sql("select appid,service,count(0) from StreamDemo_table group by appid,service").collect()
      for(row <- req){
        val appid = row.getString(0)
        val service = row.getString(1)
        val count = row.getLong(2)
        printf("(s%,s%) -> d%\n",appid,service,count)
      }

      //性能统计
      val perf = sqlContext.sql("select service,min(requestTime),max(requestTime),avg(requestTime) from StreamDemo_table group by service").collect()
      for(row<-perf){
        val service = row.getString(0)
        val min = row.getLong(1)
        val max = row.getLong(2)
        val avg = row.getDouble(3)
        printf("s% -> d%,d%,f%",service,min,max,avg)
      }
    }
    else{
      printf("elem is empty!!!")
    }

    def main(args: Array[String]): Unit = {
      val sparkConf = new SparkConf().setAppName("StreamDemo")
      //初始化streamingcontext
      val ssc = new StreamingContext(sparkConf,Seconds(10))
      ssc.checkpoint("checkpoint\\streamDemo")
      //初始化SQLcontext
      val sc = ssc.sparkContext
      val sqlContext = new SQLContext(sc)

      //设置kafka的信息
      //topic信息，demolog
      val topic = "demolog"
      val topicMap = topic.split(",").map((_,1)).toMap
      //配置kafka的参数信息
      val kafkaParams = Map(
        "zookeeper.connect" -> "*.*.*.*:2181,*.*.*.*:2181,*.*.*.*:2181/kafka-test",
        "auto.offset.reset" -> "smallest",//"largest"
        "auto.commit.enable" -> "true",
        "auto.commit.interval.ms" -> "30000",
        "zookeeper.connection.timeout.ms" -> "10000",
        "group.id" -> "sparkDemo",
        "fetch.message.max.bytes" -> "10485760",
        "fetch.size" -> "4096000"
      )
      val lines = KafkaUtils.createStream[String,String,StringDecoder,StringDecoder](ssc,kafkaParams,topicMap,StorageLevel.MEMORY_AND_DISK).map(_._2)
      println("start to run [streamDemo] ...")
      lines.foreachRDD(x=>processLines(sqlContext,x))

    }
  }
}
