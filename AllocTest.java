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
    static double heapSize = Runtime.getRuntime().freeMemory();

    ArrayList<byte[]> longLived = new ArrayList<byte[]>();
    ArrayList<byte[]> shortLived = new ArrayList<byte[]>();
    ArrayList<byte[]> largeObjects = new ArrayList<byte[]>();

    public static void main( String args[] )
    {
        AllocTest ac = new AllocTest();
        ac.start();
    }

    public void start()
    {
        // Alloc long lived objects.
        new Thread( new LongLivedObjectsFoundry( heapSize * 0.2 ) ).start();
        // Alloc large objects.
        // Every 30 secs, fork thread that allocates short lived objects.
        // Every 2 mins, replace long lived objects.
        // Every 3 mins, replace large objects.
    }

    /**
     * This Foundry will create a certain amount of long lived objects.
     * The objects will be kept alive for a certain amount of time.
     * This is to mimic the fact that a certain percentage of the objects
     * created by real world applications do survive for a certain amount of time.
     * DaCapo and SPECjvm benchmarks showed that between 65% and 96% of objects
     * do not survive 64KByte of allocations. This Foundry will allocate between
     * 15 and 25% of the heap with long lived objects.
     */
    public class LongLivedObjectsFoundry implements Runnable
    {
        // Foundry wake up cycle, in millis.
        private long loopTime = 1000 ;
        private final double longLivedObjMem;

        public LongLivedObjectsFoundry( double longLivedObjMem )
        {
            this.longLivedObjMem = longLivedObjMem;
        }

        public void run()
        {
            System.out.println( "LongLivedObjectsFoundry started...\n" +
                "The heap memory dedicated to long lived objects is " + longLivedObjMem + " bytes." );

            while( true )
            {
                // Replace long lived objects.
                longLived.clear();
                longLived.trimToSize();

                double allocatedMem = 0;
                while( longLivedObjMem > allocatedMem )
                {
                    longLived.add( new byte[ 1024 ] );
                    allocatedMem += 1024;
                }

                System.out.println( "free mem " + Runtime.getRuntime().freeMemory() );
                try
                {
                    Thread.sleep( loopTime );
                }
                catch( InterruptedException ex )
                {
                    System.err.println( "This should not have happened...\n" + ex );
                    System.exit( 1 );
                }
            }
        }
    }
}
