import java.net.URL
import java.util.regex.Pattern
import org.apache.log4j._;
import com.sun.jersey.spi.StringReader
import de.l3s.boilerpipe.sax.BoilerpipeSAXInput
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext._
import org.xml.sax.InputSource;
import de.l3s.boilerpipe.extractors;
import java.io.StringReader;
import de.l3s.boilerpipe.sax.HTMLHighlighter;
import org.cyberneko.html.HTMLConfiguration
import scala.collection.JavaConversions._
import java.io._
import com.esotericsoftware.kryo.Kryo
import org.apache.spark.serializer.KryoRegistrator

class MyRegistrator extends KryoRegistrator {
  override def registerClasses(kryo: Kryo) {
    kryo.register(classOf[PrintWriter])
  }
}

object SimpleApp {
  def createSparkContext(): SparkContext = {

    val conf = new SparkConf().setAppName("Simple Application")
    conf.set("spark.executor.memory", "100g");
    conf.set("spark.default.parallelism","200");
    conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    conf.set("spark.kryo.registrator", "MyRegistrator")
    // Master is not set => use local master, and local data
    if (!conf.contains("spark.master")) {
      conf.setMaster("local[*]")
      conf.set("data", "data/sample.warc")
    } else {
      conf.set("data", "/mnt/cw12/cw-data/ClueWeb12_00/")
    }

    new SparkContext(conf)
  }

  def writeToFile(p: String, s: String): Unit = {
    val pw = new PrintWriter(new File(p))
    try pw.write(s) finally pw.close()
  }
  def main(args: Array[String]) {
    val log = Logger.getLogger(SimpleApp.getClass().getName());
    val layout = new SimpleLayout();
    val appender = new FileAppender(layout,"data/log.txt",false);
    log.addAppender(appender);
    log.info("Hello this is an info message");
    val sc = createSparkContext()
    val logFile = args(0)
    val min_partitions = args(1).toInt
    val getAnchors= args(2).toBoolean
    val outputPath = args(3)
    //val min_partitions = 30;
    //val logFile = sc.getConf.get("data")
    val files = sc.wholeTextFiles(logFile, min_partitions)
    val words=  files.flatMap(x =>
					x._2.split("Content-Length").drop(1)//.drop(2)
                    //x._2.split("WARC/1.0").drop(2)
                )
                //.map(doc => doc.substring(doc.indexOf("\n\r", 1+doc.indexOf("\n\r")))).filter(doc => !doc.isEmpty())
      .map(doc => doc).filter(doc => !doc.isEmpty())
      .flatMap(doc => {
      try {
        val anchors = List()
        val textDocument = new BoilerpipeSAXInput(new InputSource(new java.io.StringReader(doc))).getTextDocument()
        val originalDoc = textDocument.getTextBlocks()
        var documentContent = extractors.ArticleExtractor.INSTANCE.getText(textDocument)
        //val f_id = outputPath+java.util.UUID.randomUUID.toString
        var f_id = outputPath + "/"
        val indexofID = doc.indexOf("WARC-TREC-ID")
        println(indexofID)
        if (indexofID > 0) {
          f_id = f_id + doc.slice(indexofID + 14, indexofID + 39)
        }
        val indexofURI = doc.indexOf("WARC-Target-URI")
        if (indexofURI > 0) {
          documentContent = doc.slice(indexofURI + 17, doc.length()) + documentContent
        }
        writeToFile(f_id, documentContent)
        if (getAnchors) {
          textDocument.getTextBlocks().foreach(hhh => {
            var cur_elem = -1;
            val settedBits = hhh.getContainedTextElements()
            do {
              cur_elem = settedBits.nextSetBit(1 + cur_elem)
              if (textDocument.anchors.containsKey(cur_elem)) {
                val cur_href = textDocument.anchors.get(cur_elem);
                //anchors.+(cur_href(0))
              }
            }
            while (cur_elem != -1)
          });
        }

        documentContent.split(" ")
      }
      catch {
        case e: Exception => {
          Array("")
        }
      }
    })
    val counts = words.map(word => (word, 1)).reduceByKey(_ + _)

    val top_words = counts.top(100)(Ordering.by[(String, Int), Int](_._2))
    top_words.foreach(x => {
      println(x._1 + ":" + x._2)
    })
  }
}
