#!/usr/bin/python2.7

"""
This script generates Java classes of variable size.
It takes 2 arguments:
    class_name: the name of the class to be generated.
    object_size: the size of the objects instances of this class.
                 Note that this is an estimate based on the fact
                 that the long primitive type in Java is 64-bits in size.
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

def main(argv):
    class_name = ''
    object_size = ''

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
    main( sys.argv[1:] )