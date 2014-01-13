/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jaq.spsc;

import static io.jaq.util.UnsafeAccess.UNSAFE;
import io.jaq.BatchConsumer;
import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.util.Pow2;
import io.jaq.util.UnsafeAccess;

abstract class FFBufferOfferBatchCqPad1 {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class FFBufferOfferBatchCqColdFields<E> extends FFBufferOfferBatchCqPad1 {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 2);
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;
    protected ConcurrentQueueConsumer<E> consumer;
    protected ConcurrentQueueProducer<E> producer;

    @SuppressWarnings("unchecked")
    public FFBufferOfferBatchCqColdFields(int capacity) {
        if (Pow2.isPowerOf2(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.findNextPositivePowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }
}

public final class FFBufferWithOfferBatchCq<E> extends FFBufferOfferBatchCqColdFields<E> implements
        ConcurrentQueue<E> {
    // Layout field/data offsets are runtime constants
    private static final long TAIL_OFFSET;
    private static final long HEAD_OFFSET;
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE
                    .objectFieldOffset(ProducerFields.class.getDeclaredField("tail"));
            HEAD_OFFSET = UnsafeAccess.UNSAFE
                    .objectFieldOffset(ConsumerFields.class.getDeclaredField("head"));
            final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
            if (4 == scale) {
                ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
            } else if (8 == scale) {
                ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
            } else {
                throw new IllegalStateException("Unknown pointer size");
            }
            // Including the buffer pad in the array base offset
            ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class)
                    + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    static abstract class ProducerFieldsPad0 {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;
    }

    static abstract class ProducerFields<E> extends ProducerFieldsPad0 {
        protected final E[] buffer;
        protected final long mask;
        protected long tail;
        protected long batchTail;

        public ProducerFields(E[] buffer, long mask) {
            this.buffer = buffer;
            this.mask = mask;
        }
    }

    static final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        public Producer(E[] buffer, long mask) {
            super(buffer, mask);
        }

        private long offset(long index) {
            return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
        }

        @Override
        public boolean offer(final E e) {
            if (null == e) {
                throw new NullPointerException("Null is not a valid element");
            }

            if (tail >= batchTail) {
                if (null != UNSAFE.getObjectVolatile(buffer, offset(tail + OFFER_BATCH_SIZE))) {
                    return false;
                }
                batchTail = tail + OFFER_BATCH_SIZE;
            }
            UNSAFE.putOrderedObject(buffer, offset(tail), e);
            tail++;

            return true;
        }

        @Override
        public boolean offer(E[] ea) {
            if (null == ea) {
                throw new NullPointerException("Null is not a valid element");
            }

            long requiredTail = tail + ea.length - 1;
            if (requiredTail >= batchTail) {
                if (null != UNSAFE.getObjectVolatile(buffer, offset(requiredTail + OFFER_BATCH_SIZE))) {
                    return false;
                }
                batchTail = requiredTail + OFFER_BATCH_SIZE;
            }
            // TODO: a case can be made for copying the array instead of inserting individual elements
            // when the elements are not sparse using System.copyArray
            int i = 0;
            for (; tail <= requiredTail; tail++) {
                UNSAFE.putOrderedObject(buffer, offset(tail), ea[i++]);
            }
            return true;
        }

        private long getTailForSize() {
            return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET), tail);
        }
    }

    static abstract class ConsumerFieldsPad0<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;
    }

    static abstract class ConsumerFields<E> extends ConsumerFieldsPad0<E> {
        protected final long mask;
        protected final E[] buffer;
        protected long head = 0;

        public ConsumerFields(E[] buffer, long mask) {
            this.buffer = buffer;
            this.mask = mask;
        }
    }

    static final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        private Consumer(E[] buffer, long mask) {
            super(buffer, mask);
        }

        private long offset(long index) {
            return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
        }

        @Override
        public E poll() {
            final long offset = offset(head);
            @SuppressWarnings("unchecked")
            final E e = (E) UnsafeAccess.UNSAFE.getObjectVolatile(buffer, offset);
            if (null == e) {
                return null;
            }
            UnsafeAccess.UNSAFE.putOrderedObject(buffer, offset, null);
            head++;
            return e;
        }

        @SuppressWarnings("unchecked")
        @Override
        public E peek() {
            return (E) UnsafeAccess.UNSAFE.getObject(buffer, offset(head));
        }

        @Override
        public void consumeBatch(BatchConsumer<E> bc, int batchSizeLimit) {
            // TODO: this is a generic implementation, better performance can be achieved here
            boolean last;
            do {
                E e = poll();
                if (e == null) {
                    return;
                }
                last = (peek() == null) || --batchSizeLimit == 0;
                bc.consume(e, last);
            } while (!last);
        }

        @Override
        public void clear() {
            while (null != poll())
                ;
        }

        private long getHeadForSize() {
            return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET), head);
        }
    }

    public FFBufferWithOfferBatchCq(final int capacity) {
        super(capacity);
    }

    @Override
    public int size() {
        long headForSize = consumer == null ? 0 : ((Consumer<E>) consumer).getHeadForSize();
        long tailForSize = producer == null ? 0 : ((Producer<E>) producer).getTailForSize();
        return (int) (tailForSize - headForSize);
    }

    @Override
    public int capacity() {
        return capacity - OFFER_BATCH_SIZE;
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        if (consumer == null) {
            consumer = new Consumer<>(buffer, mask);
        }
        return consumer;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        if (producer == null) {
            producer = new Producer<>(buffer, mask);
        }
        return producer;
    }
}
