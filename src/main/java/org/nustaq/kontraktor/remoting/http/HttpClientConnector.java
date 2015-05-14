package org.nustaq.kontraktor.remoting.http;

import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.nustaq.kontraktor.Actor;
import org.nustaq.kontraktor.Actors;
import org.nustaq.kontraktor.IPromise;
import org.nustaq.kontraktor.Promise;
import org.nustaq.kontraktor.remoting.base.ActorClient;
import org.nustaq.kontraktor.remoting.base.ActorClientConnector;
import org.nustaq.kontraktor.remoting.base.ObjectSink;
import org.nustaq.kontraktor.remoting.base.ObjectSocket;
import org.nustaq.kontraktor.remoting.encoding.Coding;
import org.nustaq.kontraktor.remoting.encoding.SerializerType;
import org.nustaq.kontraktor.remoting.websockets.WebObjectSocket;
import org.nustaq.serialization.FSTConfiguration;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by ruedi on 13/05/15.
 * <p>
 * temp impl for testing. slow
 */
public class HttpClientConnector implements ActorClientConnector {

    public static int LP_TIMEOUT = 50000;

    String host;
    String sessionId;
    FSTConfiguration authConf = FSTConfiguration.createMinBinConfiguration();

    public HttpClientConnector(String host) {
        this.host = host;
    }

    @Override
    public void connect(Function<ObjectSocket, ObjectSink> factory) throws Exception {
        Content content = Request.Post(host)
                              .bodyByteArray(authConf.asByteArray(new Object[]{"authentication", "data"}))
                              .execute()
                              .returnContent();
        sessionId = (String) authConf.asObject(content.asBytes());
        MyHttpWS myHttpWS = new MyHttpWS(host + "/" + sessionId);
        ObjectSink sink = factory.apply(myHttpWS);
        myHttpWS.setSink(sink);
        // start longpoll
        Runnable lp[] = {null};
        lp[0] = () -> {
            myHttpWS.asyncFlush().then((r, e) -> {
                if ( r != null )
                    Actor.current().execute(lp[0]);
                else
                    Actor.current().delayed(1000,lp[0]);
            });
        };
        Actor.current().execute( lp[0] );
    }

    @Override
    public IPromise closeClient() {
        return null;
    }

    static class MyHttpWS extends WebObjectSocket {

        String url;
        ObjectSink sink;

        public MyHttpWS(String url) {
            this.url = url;
        }

        @Override
        public void sendBinary(byte[] message) {
            asyncSend(message);
        }

        public IPromise asyncSend(byte[] message) {
            Promise p = new Promise();
            Actor.current().$exec( () -> {
                try {
                    Content content = Request.Post(url)
                        .socketTimeout(LP_TIMEOUT)
                        .bodyByteArray(message)
                        .execute()
                        .returnContent();
                    return content;
                } catch (IOException e) {
                    Actors.throwException(e);
                }
                return null;
            }).then((content, ex) -> {
                if (content != null) {
                    byte[] b = ((Content) content).asBytes();
                    if (b.length > 0) {
                        Object o = getConf().asObject(b);
                        sink.receiveObject(o);
                    }
                } else {
                    ((Throwable) ex).printStackTrace();
                }
                p.complete(content, ex);
            });
            return p;
        }

        @Override
        public synchronized void writeObject(Object toWrite) throws Exception {
            super.writeObject(toWrite);
        }

        @Override
        public synchronized void flush() throws Exception {
            if ( objects.size() == 0 )
                return;
            asyncFlush();
        }

        public synchronized IPromise asyncFlush() {
            objects.add(0); // sequence
            Object[] objArr = objects.toArray();
            objects.clear();
            return asyncSend(conf.asByteArray(objArr));
        }

        @Override
        public void close() throws IOException {

        }

        public void setSink(ObjectSink sink) {
            this.sink = sink;
        }

        public ObjectSink getSink() {
            return sink;
        }
    }

    public static class DummyA extends Actor<DummyA> {

    }

    public static void main( String a[] ) throws InterruptedException {

        DummyA da = Actors.AsActor(DummyA.class);

        HttpClientConnector con = new HttpClientConnector("http://localhost:8080/http");
        ActorClient actorClient = new ActorClient(con, UndertowHttpConnector.HTTPA.class, new Coding(SerializerType.MinBin));
        da.execute(() -> {
            UndertowHttpConnector.HTTPA act = (UndertowHttpConnector.HTTPA) actorClient.connect().await();
            int count[] = {0};
            da.SubmitPeriodic(1000, time -> {
                act.hello("pok").then(r -> System.out.println("response:"+count[0]++ +" " + r));
                return time;
            });
        });
        Thread.sleep(100000000);
    }

}
