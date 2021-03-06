#!/usr/bin/python2.7

"""
This script generates a Java class whose instances are approximately of the specified size.
This is achieved by creating a class with a certain amount of long fields, which are
guaranteed to be 8 bytes in size by the JVM specifications.

Two arguments need to be provided:
    class_name:  the name of the class to be generated.
    object_size: the approximate size of the objects instances of this class.

Example:
    "TestClassGenerator.py -n LargeObj -s 256" will generate a class named
    LargeObj with 32 fields of type long (32*8=256 bytes).

NOTE: the effective size in bytes of an object depends on the JVM's specific object's layout
      and might vary between different JVM's implementations.
"""

import fileinput
import sys
import getopt

def write_header( file, class_name ):
    header_file = open( 'header' )
    for line in header_file:
        if '####' in line:
            line = line.replace( '####', class_name )
        file.write( line )

def write_fields( file, count ):
    for c in range( 0, count ):
        field_file = open( 'field', 'r' )
        for line in field_file:
            if 'field###' in line:
                line = line.replace( '###', str(c) )
                file.write( line )

def write_footer( file ):
    footer_file = open( 'footer' )
    file.write( footer_file.read() )

def print_help():
    print 'TestClassGenerator.py -n <class_name> -s <object_size>'

def run(argv):
    class_name = ''
    object_size = 0

    try:
        opts, args = getopt.getopt(argv, 'hn:s:', ['cname=', 'osize='])
    except getopt.GetoptError:
        print_help()
        sys.exit(1)
    for opt, arg in opts:
        if opt == '-h':
            print_help()
            sys.exit(0)
        elif opt in ( '-n', '--cname' ):
            class_name = arg
        elif opt in ( '-s', '--osize' ):
            object_size = int(arg)

    file_name = '%s.java' % class_name
    file = open( file_name, 'w+' )
    write_header( file, class_name )

    field_size = 8
    fields_count = object_size / field_size
    write_fields( file, fields_count )

    write_footer( file )
    file.close()

if __name__ == '__main__':
    run( sys.argv[1:] )
