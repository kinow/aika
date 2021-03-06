/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aika;

/**
 *
 * @author Lukas Molzberger
 */
public class ReadWriteLock {

    private int readers = 0;
    private int writers = 0;
    private int writeRequests = 0;
    private int writerThreadId = -1;

    private Object writeLock = new Object();

    public void acquireWriteLock(int threadId) {
        try {
            synchronized (this) {
                writeRequests++;
                while (readers > 0) {
                    wait();
                }
            }
            synchronized (writeLock) {
                if(writerThreadId != threadId) {
                    while (writers > 0) {
                        writeLock.wait();
                    }
                    writerThreadId = threadId;
                }
                writers++;
            }
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }


    public synchronized void acquireReadLock() {
        try {
            while (writeRequests > 0) {
                wait();
            }
            readers++;
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void releaseWriteLock() {
        synchronized (writeLock) {
            writers--;
            if(writers == 0) {
                writerThreadId = -1;
                writeLock.notify();
            }
        }
        synchronized(this) {
            writeRequests--;
            if(writeRequests == 0) {
                notifyAll();
            }
        }
    }


    public synchronized void releaseReadLock() {
        readers--;
        if(writeRequests > 0 && readers == 0) {
            notifyAll();
        }
    }
}