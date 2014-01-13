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
package io.jaq.jmh.spsc.throughput;

import io.jaq.spsc.SPSCQueueFactory;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.logic.BlackHole;
import org.openjdk.jmh.logic.Control;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueueThroughputBusyWithBlackholes {
    public final Queue<Integer> q = SPSCQueueFactory.createQueue();
    public final static Integer ONE = 777;

    @GenerateMicroBenchmark
    @Group("tpt")
    public void offer(Control cnt, BlackHole bh) {
        int i = 0;
        while (!q.offer(ONE) && !cnt.stopMeasurement) {
            i++;
        }
        bh.consume(i);
    }

    @GenerateMicroBenchmark
    @Group("tpt")
    public void poll(Control cnt, BlackHole bh) {
        int i = 0;
        while (q.poll() == null && !cnt.stopMeasurement) {
            i++;
        }
        bh.consume(i);
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        while (q.poll() != null)
            ;
    }
}
