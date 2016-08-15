package org.apache.raft.server.storage;

import com.google.common.base.Charsets;
import org.apache.hadoop.io.IOUtils;
import org.apache.raft.util.AtomicFileOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

/**
 * Class that represents a file on disk which persistently stores
 * a single <code>long</code> value. The file is updated atomically
 * and durably (i.e fsynced).
 */
class MetaFile {
  private static final Logger LOG = LoggerFactory.getLogger(MetaFile.class);
  private static final String TERM_KEY = "term";
  private static final String VOTEDFOR_KEY = "votedFor";
  static final long DEFAULT_TERM = 0;
  static final String EMPTY_VOTEFOR = "";

  private final File file;
  private boolean loaded = false;
  private long term;
  private String votedFor;

  MetaFile(File file) {
    this.file = file;
    term = DEFAULT_TERM;
    votedFor = EMPTY_VOTEFOR;
  }

  boolean exists() {
    return this.file.exists();
  }

  long getTerm() throws IOException {
    if (!loaded) {
      readFile();
      loaded = true;
    }
    return term;
  }

  String getVotedFor() throws IOException {
    if (!loaded) {
      readFile();
      loaded = true;
    }
    return votedFor;
  }

  void set(long newTerm, String newVotedFor) throws IOException {
    newVotedFor = newVotedFor == null ? EMPTY_VOTEFOR : newVotedFor;
    if (!loaded || (newTerm != term || !newVotedFor.equals(votedFor))) {
      writeFile(newTerm, newVotedFor);
    }
    term = newTerm;
    votedFor = newVotedFor;
    loaded = true;
  }

  /**
   * Atomically write the given term and votedFor information to the given file,
   * including fsyncing.
   *
   * @throws IOException if the file cannot be written
   */
  void writeFile(long term, String votedFor) throws IOException {
    AtomicFileOutputStream fos = new AtomicFileOutputStream(file);
    Properties properties = new Properties();
    properties.setProperty(TERM_KEY, Long.toString(term));
    properties.setProperty(VOTEDFOR_KEY, votedFor);
    try {
      properties.store(
          new BufferedWriter(new OutputStreamWriter(fos, Charsets.UTF_8)), "");
      fos.close();
      fos = null;
    } finally {
      if (fos != null) {
        fos.abort();
      }
    }
  }

  void readFile() throws IOException {
    term = DEFAULT_TERM;
    votedFor = EMPTY_VOTEFOR;
    if (file.exists()) {
      BufferedReader br = new BufferedReader(
          new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
      try {
        Properties properties = new Properties();
        properties.load(br);
        if (properties.containsKey(TERM_KEY) &&
            properties.containsKey(VOTEDFOR_KEY)) {
          term = Long.parseLong((String) properties.get(TERM_KEY));
          votedFor = (String) properties.get(VOTEDFOR_KEY);
        } else {
          throw new IOException("Corrupted term/votedFor properties: "
              + properties);
        }
      } catch(IOException e) {
        LOG.warn("Cannot load term/votedFor properties from {}", file, e);
        throw e;
      } finally {
        IOUtils.cleanup(null, br);
      }
    }
  }
}
