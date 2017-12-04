const path = require('path');
const fs = require('fs');
const express = require('express');
const body_parser = require('body-parser');
const app = express();

const static_dir = path.join(__dirname, 'static');

app.use('/', express.static(static_dir));

app.listen(9090, '0.0.0.0', () => {
   console.log(`Nodepad is listening at 0.0.0.0:9090`);
});
