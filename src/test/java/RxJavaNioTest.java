import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

public class RxJavaNioTest {

  @Test
  public void nioTest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = getServer();

    TestSubscriber<String> ts = new TestSubscriber<>();

    long start = System.currentTimeMillis();

    // we use 10 since the default rxnetty thread pool size is 8
    // you could also shrink the pool down for the same effect
    // but I couldn't be bothered finding the settings
    Observable.range(1, 10)
      // flatMap runs async Observables concurrently
      .flatMap(i ->
        HttpClient.newClient(server.getServerAddress())
          .createGet("/" + i)
          .flatMap(response ->
            response.getContent()
              .map(bytes ->
                bytes.toString(Charset.defaultCharset()) + " " +
                  "[response received on " + Thread.currentThread().getName() +
                  " at " + (System.currentTimeMillis() - start) + "]"
              )
          )
      )
      .doOnNext(System.out::println)
      .subscribe(ts);

    ts.awaitTerminalEvent();

    server.shutdown();
  }

  private HttpServer<ByteBuf, ByteBuf> getServer() {
    return HttpServer.newServer()
        .start((request, response) -> {
          String requestThread = Thread.currentThread().getName();
          // we need tp flush on each so that the response will 'stream'
          return response.writeStringAndFlushOnEach(
            // one numbered reply per second
            Observable.interval(1, 1, TimeUnit.SECONDS, Schedulers.io())
              .take(4)
              .map(i ->
                getResponseValue(request, requestThread, i)
              )
          );
        });
  }

  private String getResponseValue(HttpServerRequest<ByteBuf> request, String requestThread, Long i) {
    return request.getDecodedPath() + "-" + i + " " +
        "[request received on " + requestThread + "] " +
        "[response buffered on " + Thread.currentThread().getName() + "]";
  }
}
