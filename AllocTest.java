import java.util.*;

/**
 *
 * The aim of this test is to recreate a real world scenario of a multithreaded application
 * that creates objects of different size and with different lifetime.
 *
 * The test parameters are memory allocation time and maximum allocated objects.
 *
 */

public class AllocTest
{
    private final static double heapSize = Runtime.getRuntime().freeMemory();

    public static void main( String args[] )
    {
        AllocTest ac = new AllocTest();
        //ac.calculateMaxObjectsInHeap( ObjectsFoundry.Size.MEDIUM );
        //ac.calculateMaxObjectsInHeap( ObjectsFoundry.Size.MEDIUM );
        ac.timeAllocationInRealWorldScenario();
    }

    public void calculateMaxObjectsInHeap( ObjectsFoundry.Size sizeRange )
    {
        ArrayList<TestObj> objects = new ArrayList<TestObj>();
        long count = 0;

        try
        {
            while( true )
            {
                objects.add( ObjectsFoundry.allocObj( sizeRange ) );
                ++count;
            }
        }
        catch( OutOfMemoryError err )
        {
            objects.clear();
            objects = null;
        }

        System.out.println( count + " objects of size " + sizeRange.getSizeInBytes() +
                " allocated in a heap of " + Runtime.getRuntime().totalMemory() );
    }

    public void timeAllocationInRealWorldScenario()
    {
        // Alloc variable size long lived objects (13% of total heap size).
        Thread averageLongLived = new Thread( new ObjectsFoundry( "Average Long Lived",
                    (long)(heapSize * 0.10), ObjectsFoundry.Size.AVERAGE ) );

        Thread largeLongLived = new Thread( new ObjectsFoundry( "Large Long Lived",
                    (long)(heapSize * 0.03), ObjectsFoundry.Size.LARGE ) );

        // Allocate variable size short lived objects (45% of total heap size).
        Thread averageShortLived = new Thread( new ObjectsFoundry( "Average Short Lived",
                    (long)(heapSize * 0.35), ObjectsFoundry.Size.AVERAGE ) );

        Thread largeShortLived = new Thread( new ObjectsFoundry( "Large Short Lived",
                   (long)(heapSize * 0.10), ObjectsFoundry.Size.LARGE ) );

        printFreeMem();

        // NOTE: threads are started at the same time and short and long lived foundries are interleaved.
        // This is to guarantee that they will allocate memory concurrently in order to increase the chance
        // of heap fragmentation when short lived objects are collected.
        largeShortLived.start();
        averageShortLived.start();
        largeLongLived.start();
        averageLongLived.start();

        // Loop until all the ObjectsFoundry have finished allocating objects.
        while( largeShortLived.getState() != Thread.State.WAITING ||
               averageLongLived.getState() != Thread.State.WAITING ||
               averageShortLived.getState() != Thread.State.WAITING ||
               largeLongLived.getState() != Thread.State.WAITING
             )
        {
            // Prints stats.
            printFreeMem();

            try
            {
                Thread.sleep( 8000 );
            }
            catch( InterruptedException ex )
            {
                AllocTest.abort( ex );
            }
        }

        System.out.println( "Foundries have finished allocating objects..." );
        printFreeMem();

        // Notify short lived foundries. This will send out of scope ~45% of the allocated objects.
        System.out.println( "Notify the short lived object foundries that they can finish execution..." );

        synchronized( largeShortLived )
        {
            largeShortLived.notify();
            largeShortLived = null;
        }
        synchronized( averageShortLived )
        {
            averageShortLived.notify();
            averageShortLived = null;
        }

        // Reallocate 40% of the heap.
        calculateAllocationTime( heapSize * 0.40, ObjectsFoundry.Size.AVERAGE );
    }

    private static void printFreeMem()
    {
        System.out.println( "Free memory " + Runtime.getRuntime().freeMemory() / 1024 + " Kbytes." );
    }

    private void calculateAllocationTime( double memToAlloc, ObjectsFoundry.Size sizeRange )
    {

        int objToAlloc = (int)memToAlloc / sizeRange.getSizeInBytes();

        TestObj objects[] = new TestObj[ objToAlloc ];

        long start = System.currentTimeMillis();
        int count = 0;
        try
        {
            for( ; count < objToAlloc; ++count )
            {
                objects[ count ] = new AverageObj();

            }
        }
        catch( OutOfMemoryError err )
        {
            System.out.println( "Allocation Time test: OOM!!!" );
        }

        long allocTime = System.currentTimeMillis() - start;

        System.out.println( "Allocated " + count + " " + sizeRange +
                " objects in " + allocTime + " ms." );

        printFreeMem();
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

        private final ObjectsFoundry.Size sizeRange;
        private final long numObjToAlloc;

        ArrayList<TestObj> objects = new ArrayList<TestObj>();

        public ObjectsFoundry( String type, long dedicatedMem, ObjectsFoundry.Size sizeRange )
        {
            this.type = type;
            this.dedicatedMem = dedicatedMem;
            this.sizeRange = sizeRange;

            // Estimate the amount of object that will be allocated by this foundry.
            this.numObjToAlloc = estimateObjectsForMem( dedicatedMem , sizeRange );
        }

        public void run()
        {
            System.out.println( type + " ObjectsFoundry started...\n" +
                "The heap memory dedicated to " + type + " objects is " + dedicatedMem / 1024 + " KiB." );

            for( int i = 0; i < numObjToAlloc; ++i )
            {
                try
                {
                    objects.add( allocObj( sizeRange ) );
                }
                catch( OutOfMemoryError err )
                {
                    System.out.println( "The " + type + " ObjectFoundry terminated because of OOM." );
                    break;
                }

                try
                {
                    // Sleep for a while between allocations. This is to make sure that when multiple
                    // object foundries are running at the same time, the heap allocations are interleaved.
                    // This guarantees that the allocation of objects of the same type is not contiguous, to facilitate
                    // heap fragmentation, which is one of the aspects of real world memory allocations.
                    if( sizeRange == Size.AVERAGE )
                    {
                        Thread.sleep( 1 );
                    }
                    else if( sizeRange == Size.SMALL )
                    {
                        Thread.sleep( 2 );
                    }
                    else if( sizeRange == Size.LARGE ||
                             sizeRange == Size.HUGE )
                    {
                        Thread.sleep( 10 );
                    }
                }
                catch( InterruptedException ex )
                {
                    AllocTest.abort( ex );
                }
            }

            System.out.println( "ObjectsFoundry: Allocated " + objects.size() +
                    " " + sizeRange + " objects." );

            try
            {
                // Wait for notify. All objects are kept alive.
                Thread currentThread = Thread.currentThread();
                synchronized( currentThread )
                {
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

        public static TestObj allocObj( Size sizeRange )
        {
            switch( sizeRange )
            {
                case SMALL:
                    return new SmallObj();
                case AVERAGE:
                    return new AverageObj();
                case LARGE:
                    return new LargeObj();
                case HUGE:
                    return new HugeObj();
                default:
                    AllocTest.abort( new Throwable( "Invalid object size range." ) );
            }
            return null;
        }

        public enum Size
        {
            // The size of the objects is based on the empiric
            // observations made by several studies (Dan Lo et al., Guiton et al., Blackburn et al.).
            SMALL( "Small", 8 ),
            AVERAGE( "Average", 32 ),
            LARGE( "Large", 256 ),
            HUGE( "Huge", 4096*2 );

            private final String name;
            private final int bytesCount;

            private Size( String name, int bytesCount )
            {
                this.name = name;
                this.bytesCount = bytesCount;
            }

            public int getSizeInBytes()
            {
                return bytesCount;
            }

            public String toString()
            {
                return name;
            }
        }

        public long estimateObjectsForMem( long memory, ObjectsFoundry.Size sizeRange )
        {
            return memory / sizeRange.getSizeInBytes();
        }
    }

    private static void abort( Throwable t )
    {
        System.err.println( "This should not have happened...\n" + t );
        System.exit( 1 );
    }

    // The base class for the test objects is defined here.
    // The 4 different class size are autogenerated with a python script.

    static class TestObj {}
}

