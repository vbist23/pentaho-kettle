/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/



package org.pentaho.di.trans.steps.s3csvinput;

import java.io.IOException;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;


/**
 * @author Matt
 * @since 24-jan-2005
 */
public class S3CsvInputData extends BaseStepData implements StepDataInterface {
  public RowMetaInterface convertRowMeta;
  public RowMetaInterface outputRowMeta;

  private byte[] bb;
  public byte[] byteBuffer;
  public int    startBuffer;
  public int    endBuffer;
  public int    bufferSize;

  public byte[] delimiter;
  public byte[] enclosure;

  public int preferredBufferSize;
  public String[] filenames;
  public int      filenr;
  public int      startFilenr;
  public byte[]   binaryFilename;
  public long fileSize;

  public boolean  isAddingRowNumber;
  public long     rowNumber;
  public boolean stopReading;
  public int stepNumber;
  public int totalNumberOfSteps;
  public List<Long> fileSizes;
  public long totalFileSize;
  public long blockToRead;
  public long startPosition;
  public long endPosition;
  public long bytesToSkipInFirstFile;

  public long totalBytesRead;

  public boolean parallel;
  public AmazonS3 s3Client;
  public Bucket s3bucket;
  public int maxLineSize;
  public S3ObjectInputStream s3ObjectInputStream;

  /**
   *
   */
  public S3CsvInputData() {
    super();
    byteBuffer = new byte[]{};
    startBuffer = 0;
    endBuffer = 0;
    totalBytesRead = 0;
    bb = new byte[50000]; // TODO re-introduce as parameter, probably doesn't matter at all.
  }

  // Resize
  public void resizeByteBuffer() {
    // What's the new size?
    // It's (endBuffer-startBuffer)+size !!
    // That way we can at least read one full block of data using NIO
    //
    bufferSize = endBuffer - startBuffer;
    int newSize = bufferSize + preferredBufferSize;
    byte[] newByteBuffer = new byte[newSize];

    // copy over the old data...
    System.arraycopy( byteBuffer, startBuffer, newByteBuffer, 0, bufferSize );

    // replace the old byte buffer...
    byteBuffer = newByteBuffer;

    // Adjust start and end point of data in the byte buffer
    //
    startBuffer = 0;
    endBuffer = bufferSize;
  }

  public boolean readBufferFromFile() throws IOException {
    int n = s3ObjectInputStream.read( bb );
    if ( n == -1 ) {
      return false;
    } else {
      // adjust the highest used position...
      //
      bufferSize = endBuffer + n;

      // Store the data in our byte array
      //
      for ( int i = 0; i < n; i++ ) {
        byteBuffer[endBuffer + i] = bb[i];
      }

      return true;
    }
  }

  /**
   * Increase the endBuffer pointer by one.<br>
   * If there is not enough room in the buffer to go there, resize the byte buffer and read more data.<br>
   * if there is no more data to read and if the endBuffer pointer has reached the end of the byte buffer, we return true.<br>
   * @return true if we reached the end of the byte buffer.
   * @throws IOException In case we get an error reading from the input file.
   */
  public boolean increaseEndBuffer() throws IOException {
    endBuffer++;

    if ( endBuffer >= bufferSize ) {
      // Oops, we need to read more data...
      // Better resize this before we read other things in it...
      //
      resizeByteBuffer();

      // Also read another chunk of data, now that we have the space for it...
      if ( !readBufferFromFile() ) {
        // Break out of the loop if we don't have enough buffer space to continue...
        //
        if ( endBuffer >= bufferSize ) {
          return true;
        }
      }
    }

    return false;
  }

  /**
      <pre>
      [abcd "" defg] --> [abcd " defg]
      [""""] --> [""]
      [""] --> ["]
      </pre>

     @return the byte array with escaped enclosures escaped.
  */
  public byte[] removeEscapedEnclosures( byte[] field, int nrEnclosuresFound ) {
    byte[] result = new byte[field.length - nrEnclosuresFound];
    int resultIndex = 0;
    for ( int i = 0; i < field.length; i++ ) {
      if ( field[i] == enclosure[0] ) {
        if ( !( i + 1 < field.length && field[i + 1] == enclosure[0] ) ) {
          // if field[i]+field[i+1] is an escaped enclosure, ignore it
          // field[i+1] will be picked up on the next iteration.

          // But this is not an escaped enclosure...
          result[resultIndex++] = field[i];
        }
      } else {
        result[resultIndex++] = field[i];
      }
    }
    return result;
  }

}
