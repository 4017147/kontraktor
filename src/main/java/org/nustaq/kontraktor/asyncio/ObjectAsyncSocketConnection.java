/*
Kontraktor Copyright (c) Ruediger Moeller, All rights reserved.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3.0 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

See https://www.gnu.org/licenses/lgpl.txt
*/

package org.nustaq.kontraktor.asyncio;

import org.nustaq.kontraktor.Actor;
import org.nustaq.kontraktor.Actors;
import org.nustaq.kontraktor.remoting.base.ObjectSocket;
import org.nustaq.kontraktor.util.Log;
import org.nustaq.offheap.BinaryQueue;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.util.FSTUtil;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by moelrue on 5/7/15.
 */
public abstract class ObjectAsyncSocketConnection extends QueuingAsyncSocketConnection implements ObjectSocket {
    static AtomicInteger idCount = new AtomicInteger(0);
    int id = idCount.incrementAndGet();

    FSTConfiguration conf;
    Throwable lastError;
    ArrayList objects = new ArrayList();

    public ObjectAsyncSocketConnection(SelectionKey key, SocketChannel chan) {
        super(key, chan);
    }

    public ObjectAsyncSocketConnection(FSTConfiguration conf, SelectionKey key, SocketChannel chan) {
        super(key, chan);
        setConf(conf);
    }

    public void setConf(FSTConfiguration conf) {this.conf = conf;}

    public FSTConfiguration getConf() {
        return conf;
    }

    @Override
    public void dataReceived(BinaryQueue q) {
        checkThread();
        while ( q.available() > 4 ) {
            int len = q.readInt();
            if ( len <= 0 )
            {
                System.out.println("object len ?? "+len);
                return;
            }
            if ( q.available() >= len ) {
                byte[] bytes = q.readByteArray(len);
                receivedObject(conf.asObject(bytes));
            } else {
                q.back(4);
                break;
            }
        }
    }

    public int getId() {
        return id;
    }

    public abstract void receivedObject(Object o);

    public void writeObject(Object o) {
        if ( theExecutingThread != Thread.currentThread() && myActor != null ) {
            myActor.execute( () -> {
                try {
                    writeObject(o);
                } catch (Exception e) {
                    Log.Warn(this,e);
                }
            });
            return;
        }
        if ( myActor != null )
            myActor = Actor.current();
        checkThread();
        objects.add(o);
        if (objects.size()>100) {
            try {
                flush();
            } catch (Exception e) {
                FSTUtil.<RuntimeException>rethrow(e);
            }
        }
    }

    @Override
    public void flush() throws IOException, Exception {
        if ( theExecutingThread != Thread.currentThread() && myActor != null ) {
            myActor.execute( () -> {
                try {
                    flush();
                } catch (Exception e) {
                    Log.Warn(this,e);
                }
            });
            return;
        }
        checkThread();
        if ( objects.size() == 0 ) {
            return;
        }
        objects.add(0); // sequence
        Object[] objArr = objects.toArray();
        objects.clear();

        byte[] bytes = conf.asByteArray(objArr);
        write(bytes.length);
        write(bytes);
        tryFlush();
    }

    public Throwable getLastError() {
        return lastError;
    }

    @Override
    public void setLastError(Throwable ex) {
        lastError = ex;
    }
}
