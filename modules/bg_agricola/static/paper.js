'use strict';

function Box (x, y, w, h) {
   this.rect = [0, 0]; // w, h : width, height
   this.viewport = [0, 0, 0, 0]; // x, y, w, h
   this.objs = []; // [c/r:circle/rect, x, y, scale, d/w, d/h, lv, data]
   this.as_obj = ['r', 0, 0, 1, 1, 1, 0, this];
   this.timestamp = 0;

   this.draw_fn = null;

   this.set_viewport(x, y, w, h);
}

Box.prototype = {
   set_viewport: function (x, y, w, h) {
      this.rect[0] = w || 1;
      this.rect[1] = h || 1;
      this.viewport[0] = 0;
      this.viewport[1] = 0;
      this.viewport[2] = this.rect[0];
      this.viewport[3] = this.rect[1];
      this.as_obj[1] = x;
      this.as_obj[2] = y;
      this.as_obj[4] = this.rect[0];
      this.as_obj[5] = this.rect[1];
   },
   translate: function (dx, dy) {
      this.viewport[0] += dx;
      this.viewport[1] += dy;
   },
   sort_by_lv: function () {
      var i,j,k,max;
      for (i=this.objs.length-1; i>=1; i--) {
         max = this.objs[i][6];
         k = i;
         for (j=i-1; j>=0; j--) {
            if (this.objs[j][6]>max) {
               max = this.objs[j][6];
               k = j;
            }
         }
         if (i === k) continue;
         j = this.objs[i];
         this.objs[i] = this.objs[k];
         this.objs[k] = j;
      }
   },
   paint: function (pen, x, y, clear) {
      x = x || 0;
      y = y || 0;
      pen.save();
      pen.translate(x, y);
      pen.beginPath();
      pen.moveTo(0, 0);
      pen.lineTo(this.viewport[2], 0);
      pen.lineTo(this.viewport[2], this.viewport[3]);
      pen.lineTo(0, this.viewport[3]);
      pen.lineTo(0, 0);
      pen.clip();
      if (clear) {
         pen.clearRect(0, 0, this.viewport[2], this.viewport[3]);
      }
      pen.translate(-this.viewport[0], -this.viewport[1]);
      /*var objs = this.cross_all(
         ['r', this.viewport[0], this.viewport[1], 1, this.viewport[2], this.viewport[3]]
      );*/
      var objs = this.all();
      for (var i=objs.length-1; i>=0; i--) {
         this.draw_fn && this.draw_fn(pen, this.objs[objs[i]]);
      }
      pen.restore();
   },
   _hit_c: function (x, y, obj) {
      var r = obj[4]/2, dx = obj[1]+r-x, dy = obj[2]+r-y;
      if (dx*dx+dy*dy<=r*r) return true;
      return false;
   },
   _hit_r: function (x, y, obj) {
      if (x>=obj[1] && x<=obj[1]+obj[4] && y>=obj[2] && y<=obj[2]+obj[5])
         return true;
      return false;
   },
   _hit: function (x, y, obj) {
      switch (obj[0]) {
      case 'c':
         return this._hit_c(x, y, obj);
      case 'r':
         return this._hit_r(x, y, obj);
      }
      return false;
   },
   hit: function (x, y, special_cond_fn) {
      for(var i=this.objs.length-1; i>=0; i--) {
         if (this._hit(x, y, this.objs[i])) {
            if (special_cond_fn && !special_cond_fn(this.objs[i], i)) {
               continue;
            }
            return i;
         }
      }
      return -1;
   },
   hit_all: function (x, y, special_cond_fn) {
      var r = [];
      for(var i=this.objs.length-1; i>=0; i--) {
         if (this._hit(x, y, this.objs[i])) {
            if (special_cond_fn && !special_cond_fn(this.objs[i], i)) {
               continue;
            }
            r.push(i);
         }
      }
      return r;
   },
   _cross_c_c: function (obj1, obj2) {
      var r1 = obj1[4]/2, r2 = obj2[4]/2,
          dx = obj1[1]-obj2[1]+r1-r2,
          dy = obj1[2]-obj2[2]+r1-r2;
      if (dx*dx+dy*dy<=(r1+r2)*(r1+r2)) return true;
      return false;
   },
   _cross_r_r: function (obj1, obj2) {
      if (
         obj1[1]<=obj2[1]+obj2[4] &&
         obj1[1]+obj1[4]>=obj2[1] &&
         obj1[2]<=obj2[2]+obj2[5] &&
         obj1[2]+obj2[5]>=obj2[2]
      ) {
         return true;
      }
      return false;
   },
   _cross_r_c: function (obj_c, obj_r) {
      var v = [obj_c[1]-obj_r[1], obj_c[2]-obj_r[2]],
          r = obj_c[4]/2;
      if (v[0]<0) v[0] = -v[0];
      if (v[1]<0) v[1] = -v[1];
      v[0] -= obj_r[4];
      v[1] -= obj_r[5];
      if (v[0]<0) v[0] = 0;
      if (v[1]<0) v[1] = 0;
      if (v[0]*v[0]+v[1]*v[1]<r*r) return true;
      return false;
   },
   _cross: function (obj1, obj2) {
      if (obj1[0] === 'c') {
         if (obj2[0] === 'c') {
            return this._cross_c_c(obj1, obj2);
         } else {
            return this._cross_r_c(obj1, obj2);
         }
      } else {
         if (obj2[0] === 'c') {
            return this._cross_r_c(obj2, obj1);
         } else {
            return this._cross_r_r(obj1, obj2);
         }
      }
      return false;
   },
   cross: function (obj, special_cond_fn) {
      for(var i=this.objs.length-1; i>=0; i--) {
         if (this._cross(obj, this.objs[i])) {
            if (special_cond_fn && !special_cond_fn(this.objs[i], i)) {
               continue;
            }
            return i;
         }
      }
      return -1;
   },
   cross_all: function (obj, special_cond_fn) {
      var r = [];
      for(var i=this.objs.length-1; i>=0; i--) {
         if (this._cross(obj, this.objs[i])) {
            if (special_cond_fn && !special_cond_fn(this.objs[i], i)) {
               continue;
            }
            r.push(i);
         }
      }
      return r;
   },
   all: function () {
      var r = [];
      for(var i=this.objs.length-1; i>=0; i--) {
         r.push(i);
      }
      return r;
   }
};
