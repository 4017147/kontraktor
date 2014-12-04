package org.newstaq.kontraktor.remoting.aeron;
/*
 * Copyright 2014 Real Logic Ltd.
 *
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


import uk.co.real_logic.aeron.common.BackoffIdleStrategy;
import uk.co.real_logic.aeron.common.BusySpinIdleStrategy;
import uk.co.real_logic.aeron.common.concurrent.SigIntBarrier;
import uk.co.real_logic.aeron.driver.MediaDriver;
import uk.co.real_logic.aeron.driver.ThreadingMode;

public class LLMediaDriver
{
    public static void main(final String[] args) throws Exception
    {
        BasicPublisher.useSharedMemoryOnLinux();

        final MediaDriver.Context ctx = new MediaDriver.Context()
            .threadingMode(ThreadingMode.DEDICATED)
            .conductorIdleStrategy(new BackoffIdleStrategy(1, 1, 1, 1))
            .sharedNetworkIdleStrategy(new BusySpinIdleStrategy())
            .sharedIdleStrategy(new BusySpinIdleStrategy())
            .receiverIdleStrategy(new BusySpinIdleStrategy())
            .senderIdleStrategy(new BusySpinIdleStrategy());

        try (final MediaDriver ignored = MediaDriver.launch(ctx))
        {
            new SigIntBarrier().await();

            System.out.println("Shutdown Driver...");
        }
    }
}
