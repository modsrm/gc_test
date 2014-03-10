import java.util.*;

/**
 *
 * The aim of this test is to recreate a real world scenario of a multithreaded application
 * that creates objects of different size and with different lifetime.
 *
 * The test parameters are memory allocation time and (TODO) gc pause.
 *
 */

public class AllocTest
{
    private final static double heapSize = Runtime.getRuntime().freeMemory();

    public static void main( String args[] )
    {
        AllocTest ac = new AllocTest();
        ac.start();
    }

    public void start()
    {
        // Alloc variable size long lived objects (25% of total heap size).
        Thread smallLongLived = new Thread( new ObjectsFoundry( "Small Long Lived", (long)(heapSize * 0.1), ObjectsFoundry.Size.SMALL ) );
        Thread mediumLongLived = new Thread( new ObjectsFoundry( "Medium Long Lived", (long)(heapSize * 0.1), ObjectsFoundry.Size.MEDIUM ) );
        Thread largeLongLived = new Thread( new ObjectsFoundry( "Large Long Lived", (long)(heapSize * 0.05), ObjectsFoundry.Size.LARGE ) );

        // Alloc variable size short lived objects ( 65% of the total heap space ).
        Thread smallShortLived = new Thread( new ObjectsFoundry( "Small Short Lived",  (long)(heapSize * 0.4), ObjectsFoundry.Size.SMALL ) );
        Thread mediumShortLived = new Thread( new ObjectsFoundry( "Medium Short Lived", (long)(heapSize * 0.25), ObjectsFoundry.Size.MEDIUM ) );

        printFreeMem();

        // NOTE: threads are started at the same time and short and long lived foundries are interleaved.
        // This is to guarantee that they will allocate memory concurrently in order to increase the chance
        // of heap fragmentation when short lived objects are collected.
        smallLongLived.start();
        mediumShortLived.start();
        largeLongLived.start();
        smallShortLived.start();
        mediumLongLived.start();

        while( smallLongLived.getState() != Thread.State.WAITING ||
               smallShortLived.getState() != Thread.State.WAITING ||
               mediumLongLived.getState() != Thread.State.WAITING ||
               mediumShortLived.getState() != Thread.State.WAITING ||
               largeLongLived.getState() != Thread.State.WAITING
             )
        {
            System.out.println( "Foundries are allocating...");
            printFreeMem();

            try
            {
                Thread.sleep( 2000 );
            }
            catch( InterruptedException ex )
            {
                AllocTest.abort( ex );
            }
        }

        System.out.println( "Foundries have finished allocating objects..." );
        printFreeMem();

        // Notify short lived foundries. This will send out of scope ~65% of the allocated objects.
        System.out.println( "Notify the short lived object foundries that they can finish execution..." );

        synchronized( smallShortLived )
        {
            smallShortLived.notify();
        }
        synchronized( mediumShortLived )
        {
            mediumShortLived.notify();
        }

        calculateAllocationTime( heapSize * 0.65 );
        try
        {
            Thread.sleep( 10000000 );
        }
        catch( Throwable t )
        {
        }
    }

    private void printFreeMem()
    {
        System.out.println( "Free memory " + Runtime.getRuntime().freeMemory() / 1024 + " Kbytes." );
    }

    private void calculateAllocationTime( double memToAlloc )
    {
        // TODO: diversify the type of the objects allocated.

    }


    /**
     * This Foundry will create a certain amount of object with the specified characteristics
     * and then will wait to be notified.
     * The objects will be kept alive until the ObjectFoundry is alive.
     * To kill an ObjectsFoundry and all the objects it has allocated, sent a notify to its Thread.
     */
    public static class ObjectsFoundry implements Runnable
    {
        private final String type;
        private final long dedicatedMem;

        private final ObjectsFoundry.Size objSize;
        private final long numObjToAlloc;

        ArrayList<TestObj> objects = new ArrayList<TestObj>();

        // TODO: add doc.
        public ObjectsFoundry( String type, long dedicatedMem, ObjectsFoundry.Size objSize )
        {
            this.type = type;
            this.dedicatedMem = dedicatedMem;
            this.objSize = objSize;

            // Estimate the amount of object that will be allocated by this foundry.
            this.numObjToAlloc = estimateObjectsForMem( dedicatedMem , objSize );
        }

        public void run()
        {
            System.out.println( type + " ObjectsFoundry started...\n" +
                "The heap memory dedicated to " + type + " objects is " + dedicatedMem / 1024 + " KiB." );

            for( int i = 0; i < numObjToAlloc; ++i )
            {
                objects.add( allocObj() );

                try
                {
                    // Sleep up to 100 ms between allocations. This is to make sure that when multiple
                    // object foundries are running at the same time, the heap allocations are interleaved.
                    // This guarantees that the allocation of objects of the same type is not contiguous, to facilitate
                    // heap fragmentation, which is one of the aspects of real world memory allocations.
                    if( objSize == Size.SMALL )
                    {
                        Thread.sleep( 2 );
                    }
                    else if( objSize == Size.MEDIUM )
                    {
                        Thread.sleep( 40 );
                    }
                    else
                    {
                        Thread.sleep( 100 );
                    }
                }
                catch( InterruptedException ex )
                {
                    AllocTest.abort( ex );
                }
            }

            System.out.println( "Allocated " + objects.size() + " objects." );
            try
            {
                // Wait for notify. All objects are kept alive.
                Thread currentThread = Thread.currentThread();
                synchronized( currentThread )
                {
                    System.out.println( "Free mem " + Runtime.getRuntime().freeMemory() );
                    currentThread.wait();
                }
            }
            catch( InterruptedException ex )
            {
                AllocTest.abort( ex );
            }

            System.out.println( type + " ObjectsFoundry terminated..." );
            printFreeMem();
        }

        private TestObj allocObj()
        {
            switch( objSize )
            {
                case SMALL:
                    return new SmallObj();
                case MEDIUM:
                    return new MediumObj();
                case LARGE:
                    return new LargeObj();
                case HUGE:
                    return new HugeObj();
                default:
                    AllocTest.abort( new Throwable( "Invalid object size." ) );
            }
            return null;
        }

        public enum Size
        {
            SMALL( "Small", 128 ),
            MEDIUM( "Medium", 2048 ),
            LARGE( "Large", 4096 ),
            HUGE( "Huge", 4096*16 );

            private final String name;
            private final int bytesCount;

            private Size( String name, int bytesCount )
            {
                this.name = name;
                this.bytesCount = bytesCount;
            }

            public int getByteCount()
            {
                return bytesCount;
            }

            public String toString()
            {
                return name;
            }
        }

        public long estimateObjectsForMem( long memory, ObjectsFoundry.Size objSize )
        {
            return memory / objSize.getByteCount();
        }
    }

    private static void abort( Throwable t )
    {
        System.err.println( "This should not have happened...\n" + t );
        System.exit( 1 );
    }
}

