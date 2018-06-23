const i_fs = require('fs');
const i_path = require('path');


function extract_require(filename) {
   let base = i_path.resolve(i_path.dirname(filename));
   let text = i_fs.readFileSync(filename).toString();
   // TODO: support import {...} from ...;
   let n = text.length, i = 0;
   let tokens = [];
   let state = {
      current: 0
   };
   for (i = 0; i < n; i++) {
      ch = text[i];
      if (ch === '"' || ch === '\'' || ch === '`') {
         // 0 --> 1: " ' `
         if (state.current === 0) {
            state.start = i;
            state.stop = ch;
            state.current = 1;
         } else if (state.stop === ch) {
            tokens.push({
               start: state.start+1,
               end: i
            });
            state.current = 0;
            state.stop = null;
            state.start = null;
         }
      } else if (ch === '\\' && state.current === 1) {
         // 1: \
         i ++;
      } else if (ch === '/') {
         if (state.current === 0 && text[i+1] === '/') {
            // 0 ---> 2: // line comment
            state.current = 2;
         } else if (state.current === 0 && text[i+1] === '*') {
            // 0 ---> 3: // multiple line comment
            state.current = 3;
         } else if (state.current === 3 && text[i-1] === '*') {
            state.current = 0;
         }
      } else if (ch === '\n' && state.current === 2) {
         state.current = 0;
      }
   }

   // tokens now contains strings (start, end)
   tokens = tokens.filter((x) => 'require(' === text.substring(x.start-9, x.start-1));
   tokens = tokens.map((x) => text.substring(x.start, x.end));
   tokens = tokens.map((x) => {
      if (x[0] === '.') {
         return i_path.resolve(i_path.join(base, x));
      }
      return x;
   });
   return tokens;
}

function extract(filename) {
   let deps = {};
   let queue = [i_path.resolve(filename)];
   while (queue.length > 0) {
      let one = queue.pop();
      let tokens = extract_require(one);
      tokens = tokens.map((x) => {
         if (x[0] !== '/') return x;
         let ext = i_path.extname(x);
         if (ext[0] !== '.') x += '.js';
         return x;
      });
      deps[one] = tokens;
      tokens.forEach((x) => {
         if (deps[x]) return;
         if (x[0] !== '/') {
            deps[x] = null;
            return;
         }
         queue.push(x);
      });
   }
   return deps
}

console.log(JSON.stringify(extract(process.argv[2]), null, 3));