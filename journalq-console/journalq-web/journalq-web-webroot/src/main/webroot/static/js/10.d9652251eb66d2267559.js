webpackJsonp([10,17],{OCLY:function(e,t){},YF99:function(e,t){},adcC:function(e,t,a){"use strict";Object.defineProperty(t,"__esModule",{value:!0});var r=a("1a0f"),o=(a("vcXF"),a("T0gc")),i=a("95hR"),n={name:"brokerTab",components:{myTable:o.a},mixins:[i.a],data:function(){return{urls:{search:"/broker/search",removeBroker:"/brokerGroup/updateBroker"},searchData:{brokerGroupId:this.$route.query.id,keyword:""},searchRules:{},tableData:{rowData:[],colData:[{title:"ID",key:"id"},{title:"IP",key:"ip"},{title:"端口",key:"port"},{title:"数据中心",key:"dataCenter.id"},{title:"重试方式",key:"retryType"},{title:"描述",key:"description"}],btns:[{txt:"移除",method:"on-del"}]}}},computed:{},methods:{getList:function(){var e=this;this.showTablePin=!0;var t={pagination:{page:this.page.page,size:this.page.size},query:{brokerGroupId:this.$route.query.id,keyword:this.searchData.keyword}};r.a.post(this.urlOrigin.search,{},t).then(function(t){t.data=t.data||[],t.pagination=t.pagination||{totalRecord:t.data.length},e.page.total=t.pagination.totalRecord,e.page.page=t.pagination.page,e.page.size=t.pagination.size,e.tableData.rowData=t.data,e.showTablePin=!1})},del:function(e){var t=this,a={id:e.id,group:{id:-1}};r.a.put(this.urls.removeBroker+"/"+e.id,{},a).then(function(e){t.getList()})}},mounted:function(){this.getList()}},s={render:function(){var e=this,t=e.$createElement,a=e._self._c||t;return a("div",[a("div",{staticClass:"ml20 mt30"},[a("d-input",{staticClass:"left mr10",staticStyle:{width:"10%"},attrs:{placeholder:"请输入ID/分组编码/IP"},model:{value:e.searchData.keyword,callback:function(t){e.$set(e.searchData,"keyword",t)},expression:"searchData.keyword"}},[a("icon",{attrs:{slot:"suffix",name:"search",size:"14",color:"#CACACA"},on:{click:e.getList},slot:"suffix"})],1)],1),e._v(" "),a("my-table",{attrs:{data:e.tableData,showPin:e.showTablePin,page:e.page},on:{"on-size-change":e.handleSizeChange,"on-current-change":e.handleCurrentChange,"on-del":e.del}})],1)},staticRenderFns:[]};var d=a("VU/8")(n,s,!1,function(e){a("OCLY")},"data-v-92509b1c",null);t.default=d.exports},qjSX:function(e,t,a){"use strict";Object.defineProperty(t,"__esModule",{value:!0});var r=a("95hR"),o={name:"brokerGroupDetail",components:{BrokerTab:a("adcC").default},mixins:[r.a],data:function(){return{brokerGroup:{id:0,code:"",name:""}}},methods:{gotoList:function(){this.$router.push({name:"/setting/brokerGroup"})}},created:function(){this.brokerGroup.id=this.$route.query.id,this.brokerGroup.code=this.$route.query.code,this.brokerGroup.name=this.$route.query.name}},i={render:function(){var e=this,t=e.$createElement,a=e._self._c||t;return a("div",{staticClass:"clearfix",staticStyle:{margin:"20px"}},[a("d-breadcrumb",{staticClass:"mb20",attrs:{separator:">"}},[a("d-breadcrumb-item",{attrs:{to:{name:"/"+e.$i18n.locale+"/setting/brokerGroup"}}},[e._v("分组管理")]),e._v(" "),a("d-breadcrumb-item",[e._v(e._s(e.brokerGroup.name))])],1),e._v(" "),a("div",{staticClass:"detail mb20"},[a("div",{staticClass:"title"},[e._v(e._s(e.brokerGroup.name))]),e._v(" "),a("grid-row",{attrs:{gutter:16}},[a("grid-col",{attrs:{span:"8"}},[a("span",[e._v("ID:")]),e._v(" "),a("span",[e._v(e._s(e.brokerGroup.id))])]),e._v(" "),a("grid-col",{attrs:{span:"8"}},[a("span",[e._v("编码:")]),e._v(" "),a("span",[e._v(e._s(e.brokerGroup.code))])]),e._v(" "),a("grid-col",{attrs:{span:"8"}},[a("span",[e._v("名称:")]),e._v(" "),a("span",[e._v(e._s(e.brokerGroup.name))])])],1)],1),e._v(" "),a("d-tabs",[a("d-tab-pane",{attrs:{label:"Broker",name:"name2",icon:"file-text"}},[a("broker-tab")],1)],1)],1)},staticRenderFns:[]};var n=a("VU/8")(o,i,!1,function(e){a("YF99")},"data-v-219e638a",null);t.default=n.exports}});
//# sourceMappingURL=10.d9652251eb66d2267559.js.map