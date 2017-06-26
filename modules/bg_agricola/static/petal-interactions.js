/*
 * @auther: Seven Lju
 * @date:   2014-12-22
 * PetalInteraction
 *   callback : mouse event callback, e.g.
 *     {mousemove: function (target, positions, extra) { ... }}
 *      mosueevent = click, dblclick, comboclick,
 *                   mousemove, mousedown, mouseup,
 *                   mousehold, mouseframe, mousegesture
 *      positions = [x, y] or [x, y, t] or [[x1, y1], [x2, y2], ...] or
 *                  [[x1, y1, t1], [x2, y2, t2], ...] or
 *                  [[[x1, y1, t1], [x2, y2, t2], ...],
 *                   [[x1, y1, t1], ...], ...]
 *
 * PetalMobileInteraction
 *    callback: pinch event, e.g.
 *      {touchpinch: function (target, positions) { ... }}
 *       positions = [[[xStart, yStart], [xCurrent, yCurrent]], ...]
 */
function PetalInteraction(callback) {

  var config = {
    axis: {
      x: 'clientX',
      y: 'clientY'
    },
    hold: { /* hold cursor in a place for some time */
      enable: true,
      last: 1000        /* ms */,
      tolerance: 5      /* px */
    },
    combo: { /* click for N times (N > 2) */
      enable: true,
      holdable: false   /* hold for seconds and combo */,
      timingable: false /* count time for combo interval */,
      tolerance: -1     /* px, combo click (x,y) diff */,
      timeout: 200      /* ms, timeout to reset counting */
    },
    frame: { /* drag-n-drop to select an area of frame (rectangle) */
      enable: true,
      minArea: 50       /* px^2, the minimum area that a frame will be */,
      moving: false     /* true: monitor mouse move,
                                 if (x,y) changes then triggered;
                           false:
                                 if mouse up by distance then triggered*/
    },
    gesture: { /* experimental feature, customized gesture recorder
                  FIXME: it is a little slow on mobile device */
      enable: false,
      mode: 'relative'  /* absolute / relative */,
      timeout: 500      /* ms, timeout to complete gesture */,
      absolute: {
        resolution: 20  /* px, split into N*N boxes */
      },
      relative: {
        resolution: 20  /* px, mark new point over N px */
      }
    }
  };

  var target = null;
  var state = {}, lock = {};
  interaction_init();
  if (!callback) callback = {};

  function interaction_init() {
    state.hold = null;
    state.combo = null;
    state.gesture = null;
    state.frame = null;

    if (lock.checkHold !== null) clearTimeout(lock.checkHold);
    if (lock.checkCombo !== null) clearTimeout(lock.checkCombo);
    lock.holdBeatCombo = false;
    lock.mouseDown = false;
    lock.checkHold = null;
    lock.checkCombo = null;
    lock.checkGesture = null;
  }

  function clone_mouse_event(e) {
    var result = {
      type: e.type,
      altKey: e.altKey,
      ctrlKey: e.ctrlKey,
      shiftKey: e.shiftKey,
      button: e.which || e.button
    };
    result[config.axis.x] = e[config.axis.x];
    result[config.axis.y] = e[config.axis.y];
    return result;
  }

  function check_distance(x0, y0, x, y, d) {
    if (d < 0) return false;
    var dx = x - x0, dy = y - y0;
    return Math.sqrt(dx*dx+dy*dy) > d;
  }

  function check_combo() {
    switch (state.combo.count) {
    case 0: // move out and then in
      break;
    case 1: // click
      if (callback.click) callback.click(target, state.combo.positons[0]);
      break;
    case 2: // double click
      if (callback.dblclick) callback.dblclick(target, state.combo.positions);
      break;
    default: // combo click
      if (callback.comboclick) callback.comboclick(target, state.combo.positions);
    }
    lock.holdBeatCombo = false;
    lock.checkCombo = null;
    state.combo = null;
  }

  function check_hold() {
    if (lock.mouseDown) {
      if (callback.mousehold) callback.mousehold(target, [state.hold.x, state.hold.y]);
      if (!config.combo.holdable) lock.holdBeatCombo = true;
    }
    lock.checkHold = null;
    state.hold = null;
  }

  function check_gesture() {
    var fullpath = state.gesture.fullpath;
    if (fullpath.length) {
      if (fullpath.length > 1 || fullpath[0].length > 1) {
        if (callback.mousegesture) callback.mousegesture(target, fullpath);
      }
    }
    fullpath = null;
    lock.checkGesture = null;
    state.gesture = null;
  }

  function do_combo_down(x, y) {
    if (!lock.mouseDown) return;
    if (lock.checkCombo !== null) clearTimeout(lock.checkCombo);
    if (!state.combo) state.combo = {count: 0, positions: []};
    state.combo.x = x;
    state.combo.y = y;
    state.combo.timestamp = new Date().getTime();
    state.combo.count ++;
  }

  function do_combo_up(x, y) {
    if (!state.combo) return;
    var pos = [x, y];
    if (config.combo.timingable) {
      pos[2] = new Date().getTime() - state.combo.timestamp;
    }
    state.combo.positions.push(pos);
    if (config.combo.tolerance >= 0) {
      if (check_distance(state.combo.x, state.combo.y,
                         x, y, config.combo.tolerance)) {
        // mouse moved
        check_combo();
        return;
      }
    }
    if (!config.combo.holdable) {
      if (lock.holdBeatCombo) {
        // mouse hold
        check_combo();
        return;
      }
    }
    lock.checkCombo = setTimeout(check_combo, config.combo.timeout);
  }

  function do_hold(x, y, checkMoved) {
    // if mouse not down, skip
    if (!lock.mouseDown) return;
    if (checkMoved !== true) checkMoved = false;
    if (checkMoved && state.hold) {
      // if move a distance and stop to hold
      if (!check_distance(state.hold.x, state.hold.y,
                          x, y, config.hold.tolerance)) {
        return;
      }
    }
    // recount time
    if (lock.checkHold !== null) clearTimeout(lock.checkHold);
    if (!state.hold) state.hold = {};
    state.hold.x = x;
    state.hold.y = y;
    lock.checkHold = setTimeout(check_hold, config.hold.last);
  }

  function do_frame_down(x, y) {
    if (!state.frame) state.frame = {};
    state.frame.start = true;
    state.frame.x = x;
    state.frame.y = y;
  }

  function do_frame(x, y) {
    if (!state.frame) return
    if (!state.frame.start) return;
    var dx, dy;
    dx = x - state.frame.x;
    dy = y - state.frame.y;
    if (Math.abs(dx*dy) >= config.frame.minArea) {
      if (callback.mouseframe) {
        callback.mouseframe(
          target,
          [ [state.frame.x, state.frame.y], [x, y] ]
        );
      }
    }
  }

  function do_frame_up(x, y) {
    do_frame(x, y);
    state.frame = null;
  }

  function do_gesture_start(x, y) {
    if (lock.checkGesture !== null) clearTimeout(lock.checkGesture);
    if (!state.gesture) state.gesture = {};
    if (!state.gesture.fullpath) state.gesture.fullpath = [];
    state.gesture.timestamp = new Date().getTime();
    state.gesture.positions = [];
    state.gesture.x = x;
    state.gesture.y = y;
  }

  var _act_gesture_move_map_mode = {
    absolute: do_gesture_absolute,
    relative: do_gesture_relative
  };
  function do_gesture_move(x, y) {
    if (!lock.mouseDown) return;
    var point = _act_gesture_move_map_mode[config.gesture.mode](x, y);
    var timestamp = new Date().getTime();
    if (!point) return;
    state.gesture.positions.push([
      point[0], point[1],
      timestamp - state.gesture.timestamp
    ]);
    state.gesture.x = point[0];
    state.gesture.y = point[1];
    state.gesture.timestamp = timestamp;
    point = null;
  }

  function do_gesture_absolute(x, y) {
    var resolution = config.gesture.absolute.resolution;
    if (resolution < 1) resolution = 1;
    if (Math.floor(x / resolution) === Math.floor(state.x /resolution) &&
        Math.floor(y / resolution) === Math.floor(state.y /resolution)) {
      return null;
    }
    return [x, y];
  }

  function do_gesture_relative(x, y) {
    if (!check_distance(state.gesture.x, state.gesture.y, x, y,
                        config.gesture.relative.resolution)) return null;
    return [x, y];
  }

  function do_gesture_break(x, y) {
    var timestamp = new Date().getTime();
    state.gesture.positions.push([
      state.gesture.x, state.gesture.y,
      timestamp - state.gesture.timestamp
    ]);
    state.gesture.fullpath.push(state.gesture.positions);
    state.gesture.positions = [];
    state.gesture.timestamp = timestamp;
    lock.checkGesture = setTimeout(check_gesture, config.gesture.timeout);
  }

  function event_mousedown(e) {
    var x = e[config.axis.x], y = e[config.axis.y];
    lock.mouseDown = true;
    if (config.hold.enable) do_hold(x, y, false);
    if (config.combo.enable) do_combo_down(x, y);
    if (config.frame.enable) do_frame_down(x, y);
    if (config.gesture.enable) do_gesture_start(x, y);
    if (callback.mousedown) callback.mousedown(target, [x, y], clone_mouse_event(e));
  }

  function event_mousemove(e) {
    var x = e[config.axis.x], y = e[config.axis.y];
    if (config.hold.enable) do_hold(x, y, true);
    if (config.frame.enable && config.frame.moving) do_frame(x, y);
    if (config.gesture.enable) do_gesture_move(x, y);
    if (callback.mousemove) callback.mousemove(target, [x, y], clone_mouse_event(e));
  }

  function event_mouseup(e) {
    var x = e[config.axis.x], y = e[config.axis.y];
    lock.mouseDown = false;
    if (config.combo.enable) do_combo_up(x, y);
    if (config.frame.enable) do_frame_up(x, y);
    if (config.gesture.enable) do_gesture_break(x, y);
    state.hold = null;
    if (callback.mouseup) callback.mouseup(target, [x, y], clone_mouse_event(e));
  }

  function event_mouseout(e) {
    var x = e[config.axis.x], y = e[config.axis.y];
    if (lock.mouseDown) {
      if (lock.checkHold !== null) clearTimeout(lock.checkHold);
      if (lock.checkCombo !== null) clearTimeout(lock.checkCombo);
      if (config.combo.enable && state.combo) {
        check_combo();
      }
      state.hold = null;
      state.combo = null;
      lock.mouseDown = false;
    }
    if (callback.mouseout) callback.mouseout(target, [x, y], clone_mouse_event(e));
  }

  function event_mouseenter(e) {
    var x = e[config.axis.x], y = e[config.axis.y];
    var button = e.which || e.button;
    if (button > 0) {
      lock.mouseDown = true;
      if (config.hold.enable) do_hold(x, y, false);
      if (config.combo.enable) state.combo = {count: 0, positions: []};
    }
    if (callback.mouseenter) callback.mouseenter(target, [x, y], clone_mouse_event(e));
  }

  return {
    config: function () {
      return config;
    },
    bind: function (element) {
      target = element;
      interaction_init();
      element.addEventListener('mousedown', event_mousedown);
      element.addEventListener('mousemove', event_mousemove);
      element.addEventListener('mouseup', event_mouseup);
      element.addEventListener('mouseout', event_mouseout);
      element.addEventListener('mouseenter', event_mouseenter);
    },
    unbind: function () {
      target.removeEventListener('mousedown', event_mousedown);
      target.removeEventListener('mousemove', event_mousemove);
      target.removeEventListener('mouseup', event_mouseup);
      target.removeEventListener('mouseout', event_mouseout);
      target.removeEventListener('mouseenter', event_mouseenter);
      interaction_init();
      target = null;
    }
  };
}

function PetalMobileInteraction(callback) {

  var config = {
    axis: {
      x: 'clientX',
      y: 'clientY'
    },
    pinch: { /* experimental feature, support fingers pinch
                FIXME: if touch pinch triggered, mouse down
                       and mouse up will triggered too*/
      enable: false,
      moving: false,    /* monitor finger moving on display */
      tolerance: 10     /* px, finger moving > N px, event triggered */
    },
    click: {
      enable: true,
      timeout: 150,
      click: true,
      dblclick: true
    }
  };

  if (!callback) callback = {};
  var target = null;
  var state = {};

  function check_distance(x0, y0, x, y, d) {
    if (d < 0) return false;
    var dx = x - x0, dy = y - y0;
    return Math.sqrt(dx*dx+dy*dy) > d;
  }

  function check_pinch() {
    var points = state.pinch.points;
    var doit = false;
    for(var i = 0, n = points.length; i < n; i++) {
      if (check_distance(points[i][0][0], points[i][0][1],
                         points[i][1][0], points[i][1][1],
                         config.pinch.tolerance)) {
        doit = true;
        break;
      }
    }
    if (doit) {
      callback.touchpinch(target, state.pinch.points);
    }
    points = null;
  }

  function do_pinch_down(e) {
    if (!state.pinch) state.pinch = {points: null};
  }

  function do_pinch(e) {
    var touches = e.changedTouches;
    var points;
    var newpinch = false;
    if (!state.pinch.points) {
      newpinch = true;
    } else if (state.pinch.points.length < touches.length) {
      // 2 fingers to 3 and more ...
      newpinch = true;
    }
    if (newpinch) {
      var i, n, x, y;
      points = [];
      for (i = 0, n = touches.length; i < n; i++) {
        x = touches[i][config.axis.x];
        y = touches[i][config.axis.y];
        points.push([ [x, y], [x, y] ]);
      }
      state.pinch.points = points;
    } else {
      points = state.pinch.points
      var i, n, x, y;
      for (i = 0, n = points.length; i < n; i++) {
        x = touches[i][config.axis.x];
        y = touches[i][config.axis.y];
        points[i][1][0] = x;
        points[i][1][1] = y;
      }
    }
    points = null;
  }

  function do_pinch_up(e) {
    if (state.pinch.points) {
      check_pinch();
      state.pinch = null;
    }
  }

  function do_click_down(e) {
    if (!state.click) {
      state.click = {
        count: 0,
        once: true,
        checkClick: null
      };
    }
    state.click.once = true;
    if (state.click.checkClick !== null) clearTimeout(state.click.checkClick);
    state.click.checkClick = setTimeout(check_mob_click, config.click.timeout);
  }

  function do_click_up(e) {
    if (!state.click) return;
    if (!state.click.once) return;
    var touch = e.changedTouches[0];
    state.click.count ++;
    state.click.touch = {
      screenX: touch.screenX,
      screenY: touch.screenY,
      clientX: touch.clientX,
      clientY: touch.clientY,
      ctrlKey: e.ctrlKey,
      altKey: e.altKey,
      shiftKey: e.shfitKey,
      metaKey: e.metaKey
    };
    if (state.click.checkClick !== null) clearTimeout(state.click.checkClick);
    state.click.checkClick = setTimeout(check_mob_click, config.click.timeout);
  }

  function check_mob_click() {
    if (!state.click) return;
    if (state.click.count === 0) {
    } else if (state.click.count === 1 && config.click.click) {
      fire_event('click', state.click.touch);
    } else if (state.click.count === 2 && config.click.dblclick) {
      fire_event('dblclick', state.click.touch);
    } else {
      // XXX: combo click
    }
    state.click.checkClick = null;
    state.click.count = 0;
    state.click.once = false;
    state.click.touch = null;
  }

  function fire_event(type, mouse_attr) {
    // mouseAttr = {screenX, screenY, clientX, clientY, ctrlKey, altKey, shiftKey, metaKey}
    var f = document.createEvent("MouseEvents");
    f.initMouseEvent(type, true, true,
                     target.ownerDocument.defaultView, 0,
                     mouse_attr.screenX, mouse_attr.screenY,
                     mouse_attr.clientX, mouse_attr.clientY,
                     mouse_attr.ctrlKey, mouse_attr.altKey,
                     mouse_attr.shiftKey, mouse_attr.metaKey,
                     0, null);
    target.dispatchEvent(f);
  }

  var _event_touch_map_mouse = {
    touchstart: 'mousedown',
    touchmove:  'mousemove',
    touchend:   'mouseup'
  };
  function event_touch_to_mouse(e) {
    e.preventDefault();
    var touches = e.changedTouches;
    if (config.pinch.enable) {
      if (callback.touchpinch) {
        switch(e.type) {
        case 'touchmove':
          if (touches.length <= 1) break;
          if (!config.pinch.enable) return;
          if (state.pinch.count <= 1) return;
          do_pinch(e);
          if (!config.pinch.moving) return;
          check_pinch();
          return;
        case 'touchstart':
          do_pinch_down(e);
          break;
        case 'touchend':
          do_pinch_up(e);
          // next, be aware of touchend => mouseup
          break;
        }
      }
    }
    if (config.click.enable) {
      switch(e.type) {
        case 'touchstart':
          do_click_down(e);
          break;
        case 'touchend':
          do_click_up(e);
          break;
      }
    }
    fire_event(_event_touch_map_mouse[e.type], {
      screenX: touches[0].screenX,
      screenY: touches[0].screenY,
      clientX: touches[0].clientX,
      clientY: touches[0].clientY,
      ctrlKey: e.ctrlKey,
      altKey: e.altKey,
      shiftKey: e.shfitKey,
      metaKey: e.metaKey
    });
  }

  return {
    config: function() {
      return config;
    },
    bind: function (element) {
      target = element;
      element.addEventListener('touchstart', event_touch_to_mouse);
      element.addEventListener('touchmove', event_touch_to_mouse);
      element.addEventListener('touchend', event_touch_to_mouse);
    },
    unbind: function () {
      target.removeEventListener('touchstart', event_touch_to_mouse);
      target.removeEventListener('touchmove', event_touch_to_mouse);
      target.removeEventListener('touchend', event_touch_to_mouse);
      target = null;
    }
  };
}
