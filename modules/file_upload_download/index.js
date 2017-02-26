const index_dir = process.argv[2] || __dirname;
const download_uri = '/download';
const upload_uri = '/upload';

const path = require('path');

const serve_index = require('serve-index');
const express = require('express');
const app = express();

const multer = require('multer');
const storage = multer.diskStorage({
   destination: (req, file, cb) => {
      let dir = req.query.dir.substring(download_uri.length);
      cb(null, path.join(index_dir, dir));
   },
   filename: function (req, file, cb) {
      cb(null, file.originalname);
   }
});
const upload = multer({
   storage: storage,
   fileFilter: (req, file, cb) => {
      let dir = req.query.dir;
      if (dir.indexOf(download_uri + '/') != 0) {
         cb(null, false);
      } else if (dir.indexOf('/../') >= 0) {
         cb(null, false);
      } else {
         cb(null, true);
      }
   }
});

app.get('/test', (req, res) => {
   res.send('hello world!');
});

app.post(upload_uri, upload.array('uploads'), (req, res, next) => {
   res.end('done');
});

app.use(download_uri, express.static(index_dir));
app.use(download_uri, serve_index(index_dir, {
   icons: true,
   template: path.join(__dirname, 'directory.html')
}));
app.listen(9090, '0.0.0.0', () => {
   console.log(`Directory index is listening at 0.0.0.0:9090`);
});
