const fs = require('fs');

class Graph {
   constructor() {
      /*
       basic node: {id, links}
       basic link: {id, from, to}
       */
      this.nodes = {};
      this.links= {};
      this.node_autoid = 0;
      this.link_autoid = 0;
   }

   nodeAdd(obj) {
      let node = Object.assign({ links: [] }, obj);
      this.node_autoid ++;
      node.id = this.node_autoid;
      this.nodes[node.id] = node;
      return node;
   }

   nodeDel(nid) {
      let node = this.nodes[nid];
      // cascade delete
      node.links.forEach((eid) => {
         let link = this.links[eid],
             another_nid = link.from===nid?link.to:link.from;
         delete this.links[eid];
         this._linkDelInNode(eid, another_nid);
      });
      if (nid in this.nodes) delete this.nodes[nid];
      return node;
   }

   linkAdd(nidA, nidB, obj) {
      // nidA and nidB should in this.nodes
      let link = Object.assign({ from: nidA, to: nidB }, obj);
      this.link_autoid ++;
      link.id = this.link_autoid;
      this.nodes[nidA].links.push(link.id);
      if (nidA !== nidB) {
         this.nodes[nidB].links.push(link.id);
      }
      this.links[link.id] = link;
      return link;
   }

   _linkDelInNode(eid, nid) {
      let node = this.nodes[nid],
          links = [];
      node.links.forEach((seid) => {
         if (seid === eid) return;
         links.push(seid);
      })
      node.links = links;
      return links;
   }

   linkDel(eid) {
      let link = this.links[eid];
      // cascade delete
      this._linkDelInNode(eid, link.from);
      this._linkDelInNode(eid, link.to);
      return link;
   }

   save(filename) {
      let graph = { nodes:[], links: []};
      for(let nid in this.nodes) {
         graph.nodes.push(this.nodes[nid]);
      }
      for(let eid in this.links) {
         graph.links.push(this.links[eid]);
      }
      fs.writeFileSync(filename, JSON.stringify(graph));
   }

   load(filename) {
      let max_nid = 0,
          max_eid = 0,
          graph = JSON.parse(fs.readFileSync(filename));
      this.nodes = {};
      this.links = {};
      graph.nodes.forEach((node) => {
         this.nodes[node.id] = node;
         if (max_nid < node.id) max_nid = node.id;
      });
      graph.links.forEach((link) => {
         this.links[link.id] = link;
         if (max_eid < link.id) max_eid = link.id;
      });
      this.node_autoid = max_nid;
      this.link_autoid = max_eid;
   }

   nodeFilter(key, value, cmp_fn) {
      let filtered = [];
      Object.keys(this.nodes).forEach((nid) => {
         let node = this.nodes[nid];
         if (!cmp_fn(node[key], value)) return;
         filtered.push(node);
      });
      return filtered;
   }

   linkFilter(key, value, cmp_fn) {
      let filtered = [];
      Object.keys(this.links).forEach((nid) => {
         let node = this.links[nid];
         if (!cmp_fn(node[key], value)) return;
         filtered.push(node);
      });
      return filtered;
   }
}

module.exports = {
   Graph
};