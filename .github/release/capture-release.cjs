// Local semantic-release plugin: captures the computed next version and
// release notes to files, so release-prepare.yml can read them as plain
// text instead of parsing semantic-release's log output (unstructured,
// with no terminator for the notes) or shelling out commit-authored
// markdown through @semantic-release/exec (injection risk).
//
// Both hooks below run during `semantic-release --dry-run` (dry-run stops
// after generateNotes, before prepare/publish/success). generateNotes is
// accumulative: by the time this plugin's generateNotes runs — it must be
// listed LAST in .releaserc.json's plugins array — nextRelease.notes
// already holds the finished markdown from earlier plugins. This plugin
// contributes nothing to it and returns '' so the pipeline's own output
// stays exactly what release-notes-generator produced.
//
// Requires RELEASE_CAPTURE_DIR (an existing, empty-or-absent directory)
// in the environment; the caller creates it with `mkdir -p` first.

const fs = require('fs');
const path = require('path');

function captureDir() {
  const dir = process.env.RELEASE_CAPTURE_DIR;
  if (!dir) {
    throw new Error('capture-release: RELEASE_CAPTURE_DIR is not set');
  }
  return dir;
}

module.exports = {
  verifyRelease(_pluginConfig, { nextRelease }) {
    fs.writeFileSync(path.join(captureDir(), 'version'), nextRelease.version);
  },

  generateNotes(_pluginConfig, { nextRelease }) {
    fs.writeFileSync(path.join(captureDir(), 'notes.md'), nextRelease.notes || '');
    return '';
  },
};
