import java.util.*;

/**
 * This class implements the following GC tests:
 *
 * - maxObj: tests the maximum amount of objects that
 *           can be allocated on the heap. The test is
 *           executed over objects of different sizes and results
 *           are printed to std out.
 *
 * - allocTime: tests the allocation time of a batch of
 *              objects in a multithreaded scenario and
 *              prints the result to std out.
 *
 * - allocCycle: runs several allocation cycles in a multithreaded
 *               scenario and prints the state of the heap at certain
 *               time intervals to std out.
 *
 * The test name needs to be provided as argument.
 * Example:
 *   java GCTest allocCycle
 */
public class GCTest
{
    private final static double HEAPSIZE = Runtime.getRuntime().freeMemory();

    public static void main( String args[] )
    {
        GCTest ac = new GCTest();

        if( args.length < 1 )
        {
            System.out.println( "Please provide a test name..." );
            System.exit( 1 );
        }

        if( args[ 0 ].equals( "maxObj" ) )
        {
            ac.calculateMaxObjectsInHeap( ObjectsFoundry.Size.SMALL );
            ac.calculateMaxObjectsInHeap( ObjectsFoundry.Size.AVERAGE );
            ac.calculateMaxObjectsInHeap( ObjectsFoundry.Size.LARGE );
            ac.calculateMaxObjectsInHeap( ObjectsFoundry.Size.HUGE );
        }
        else if( args[ 0 ].equals( "allocTime" ) )
        {
            ac.timeAllocationInRealWorldScenario();
        }
        else if( args[ 0 ].equals( "allocCycle" ) )
        {
            ac.fullHeapCyclicAllocation( 3 );
        }
        else
        {
            System.out.println( "Test name not recognized..." );
            System.exit( 1 );
        }
    }

    /**
     * Allocates objects of the requested size range until
     * an OutOfMemoryError is thrown. It prints the amount
     * of objects allocated to std out.
     *
     * @param sizeRange the size range of the objects that will be allocated.
     */
    private void calculateMaxObjectsInHeap( ObjectsFoundry.Size sizeRange )
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

    /**
     * Creates 2 threads that allocate objects of different size to
     * account for ~60% of the heap. After that all objects are garbaged.
     * Repeats the allocation cycle for the provided amount of times.
     * Heap free memory count is printed at intervals to std out.
     *
     * @param iterations the amount of allocation cycles to be executed.
     */
    private void fullHeapCyclicAllocation( int iterations )
    {
        try
        {
            for( int i = 0; i < iterations; ++i )
            {
                Thread averageSize = new Thread( new ObjectsFoundry( "Average size",
                           (long)(HEAPSIZE * 0.4), ObjectsFoundry.Size.AVERAGE ) );

                Thread largeSize = new Thread( new ObjectsFoundry( "Average size",
                           (long)(HEAPSIZE * 0.2), ObjectsFoundry.Size.AVERAGE ) );

                printFreeMem();

                System.out.println( "Allocation cycle number " + i );
                averageSize.start();
                largeSize.start();

                // Loop until all the ObjectsFoundry have finished allocating objects.
                while( averageSize.getState() != Thread.State.WAITING ||
                       largeSize.getState() != Thread.State.WAITING )
                {
                    // Prints stats.
                    printFreeMem();

                    try
                    {
                        Thread.sleep( 8000 );
                    }
                    catch( InterruptedException ex )
                    {
                        GCTest.abort( ex );
                    }
                }

                System.out.println( "Foundries have finished allocating objects..." );
                printFreeMem();

                // Notify object foundries to garbage all the allocated objects.
                synchronized( averageSize )
                {
                    averageSize.notify();
                    averageSize = null;
                }
                synchronized( largeSize )
                {
                    largeSize.notify();
                    largeSize = null;
                }
            }
        }
        catch( OutOfMemoryError OOMe )
        {
            System.out.println( "OOM!" );
        }
    }

    /**
     * Creates 4 threads that allocate objects of different size and life span
     * to account for ~60% of the heap. After all the allocations have
     * completed, the short lived objects are garbaged and a new batch allocation
     * is started and timed. The allocation time is then printed to std out.
     */
    private void timeAllocationInRealWorldScenario()
    {
        // Alloc variable size long lived objects (13% of total heap size).
        Thread averageLongLived = new Thread( new ObjectsFoundry( "Average Long Lived",
                    (long)(HEAPSIZE * 0.10), ObjectsFoundry.Size.AVERAGE ) );

        Thread largeLongLived = new Thread( new ObjectsFoundry( "Large Long Lived",
                    (long)(HEAPSIZE * 0.03), ObjectsFoundry.Size.LARGE ) );

        // Allocate variable size short lived objects (45% of total heap size).
        Thread averageShortLived = new Thread( new ObjectsFoundry( "Average Short Lived",
                    (long)(HEAPSIZE * 0.35), ObjectsFoundry.Size.AVERAGE ) );

        Thread largeShortLived = new Thread( new ObjectsFoundry( "Large Short Lived",
                   (long)(HEAPSIZE * 0.10), ObjectsFoundry.Size.LARGE ) );

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
                GCTest.abort( ex );
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
        calculateAllocationTime( HEAPSIZE * 0.40, ObjectsFoundry.Size.AVERAGE );
    }

    /**
     * Prints the free heap memory to std out, in KB.
     */
    private static void printFreeMem()
    {
        System.out.println( "Free memory " + Runtime.getRuntime().freeMemory() / 1024 + " KB." );
    }

    /**
     * Allocates objects of the provided size range to account for the provided
     * amount of memory.
     * It prints the allocation time to std out.
     *
     * @param memToAlloc the amount of memory to be allocated, in bytes.
     * @param sizeRange the size range of the objects to be allocated.
     */
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
     * This Foundry will create a certain amount of objects with the specified characteristics
     * and then it will wait to be notified.
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
            this.numObjToAlloc = dedicatedMem / sizeRange.getSizeInBytes();
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
                    GCTest.abort( ex );
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
                GCTest.abort( ex );
            }

            System.out.println( type + " ObjectsFoundry terminated..." );
            printFreeMem();
        }

        /**
         * Allocated an object of the provides size range.
         *
         * @param sizeRange the size range of the object to be allocated.
         * @return the allocated object.
         */
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
                    GCTest.abort( new Throwable( "Invalid object size range." ) );
            }
            return null;
        }

        /**
         * The elements of this enum represent the size of the test objects.
         */
        public enum Size
        {
            // The size of the objects is based on the empiric
            // observations made by several studies (Dan Lo et al., Guiton et al., Blackburn et al.).
            SMALL( "Small", 8 ),
            AVERAGE( "Average", 48 ),
            LARGE( "Large", 256 ),
            HUGE( "Huge", 4096*2 );

            private final String name;
            private final int bytesCount;

            private Size( String name, int bytesCount )
            {
                this.name = name;
                this.bytesCount = bytesCount;
            }

            /**
             * Returns the size in bytes of the object type.
             *
             * @return the size on bytes of this object type.
             */
            public int getSizeInBytes()
            {
                return bytesCount;
            }

            @Override
            public String toString()
            {
                return name;
            }
        }
    }

    /**
     * Aborts with the provided Throwable and prints the cause
     * to std err.
     *
     * @param t the Trowable object cause of the abort.
     *
     */
    private static void abort( Throwable t )
    {
        System.err.println( "This should not have happened...\n" + t );
        System.exit( 1 );
    }

    // The base class for the test objects is defined here.
    // The 4 different class size are autogenerated with a python script.

    static class TestObj {}
}

