webpackJsonp([20],{WrV1:function(t,a){},ftqG:function(t,a,e){"use strict";Object.defineProperty(a,"__esModule",{value:!0});var i=e("1a0f"),n=e("T0gc"),o=e("fo4W"),s=e("95hR"),l={name:"application",components:{GridCol:e("r563").a,myTable:n.a,myDialog:o.a},mixins:[s.a],data:function(){return{searchData:{keyword:""},searchRules:{},tableData:{rowData:[],colData:[{title:"英文名",key:"code"},{title:"中文名",key:"name"},{title:"所属部门",key:"orgName"},{title:"邮箱",key:"email"},{title:"手机号",key:"mobile"},{title:"是否管理员",key:"role",render:function(t,a){return t("span",0===a.item.role?"用户":"管理员")}}],btns:[{txt:"角色修改  ",method:"on-edit"}]},multipleSelection:[],addDialog:{visible:!1,title:"添加用户",showFooter:!0},addData:{code:""},editDialog:{visible:!1,title:"角色修改",showFooter:!0},editData:{}}},computed:{syncUrl:function(){return this.urlOrigin.sync}},methods:{openDialog:function(t){this[t].visible=!0,this.addData.code=""},syncConfirm:function(){var t=this;i.a.get(this.syncUrl,this.syncDialogData).then(function(a){t.syncDialog.visible=!1,t.$Dialog.success({content:"同步成功"}),t.getList()})},syncCancel:function(t,a){this.syncDialog.visible=!1},handleEdit:function(t,a){console.log(t)},state:function(t,a){var e=this,n=t;n.status=a,i.a.put(this.urlOrigin.edit+"/"+n.id,{},n).then(function(t){e.$Dialog.success({content:"启用成功"}),e.getList()})},enable:function(t){this.state(t,1)},disable:function(t){this.state(t,0)},submit:function(){if(!this.addData||!this.addData.code)return this.$Message.error("请输入英文名"),!1;this.addConfirm()}},mounted:function(){this.getList()}},r={render:function(){var t=this,a=t.$createElement,e=t._self._c||a;return e("div",[e("div",{staticClass:"ml20 mt30"},[e("d-input",{staticClass:"left mr10",staticStyle:{width:"20%"},attrs:{placeholder:"请输入英文名"},model:{value:t.searchData.keyword,callback:function(a){t.$set(t.searchData,"keyword",a)},expression:"searchData.keyword"}},[e("icon",{attrs:{slot:"suffix",name:"search",size:"14",color:"#CACACA"},on:{click:t.getList},slot:"suffix"})],1),t._v(" "),e("d-button",{attrs:{type:"primary"},on:{click:function(a){return t.openDialog("addDialog")}}},[t._v("添加用户"),e("icon",{staticStyle:{"margin-left":"5px"},attrs:{name:"plus-circle"}})],1)],1),t._v(" "),e("my-table",{attrs:{data:t.tableData,showPin:t.showTablePin,page:t.page},on:{"on-size-change":t.handleSizeChange,"on-current-change":t.handleCurrentChange,"on-selection-change":t.handleSelectionChange,"on-edit":t.edit,"on-del":t.del}}),t._v(" "),e("my-dialog",{attrs:{dialog:t.addDialog},on:{"on-dialog-confirm":function(a){return t.submit()},"on-dialog-cancel":function(a){return t.addCancel()}}},[e("grid-row",{staticClass:"mb10"},[e("grid-col",{staticClass:"label",attrs:{span:3}},[t._v("英文名:")]),t._v(" "),e("grid-col",{attrs:{span:1}}),t._v(" "),e("grid-col",{staticClass:"val",attrs:{span:14}},[e("d-input",{model:{value:t.addData.code,callback:function(a){t.$set(t.addData,"code",a)},expression:"addData.code"}})],1)],1)],1),t._v(" "),e("my-dialog",{attrs:{dialog:t.editDialog},on:{"on-dialog-confirm":function(a){return t.editConfirm()},"on-dialog-cancel":function(a){return t.editCancel()}}},[e("grid-row",{staticClass:"mb10"},[e("grid-col",{staticClass:"label",attrs:{span:8}},[t._v("英文名：")]),t._v(" "),e("grid-col",{staticClass:"val",attrs:{span:16}},[t._v(t._s(t.editData.code))])],1),t._v(" "),e("grid-row",{staticClass:"mb10"},[e("grid-col",{staticClass:"label",attrs:{span:8}},[t._v("角色：")]),t._v(" "),e("grid-col",{staticClass:"val",attrs:{span:16}},[e("d-radio",{attrs:{name:"radio",label:1},model:{value:t.editData.role,callback:function(a){t.$set(t.editData,"role",a)},expression:"editData.role"}},[t._v("管理员")]),t._v(" "),e("d-radio",{attrs:{name:"radio",label:0},model:{value:t.editData.role,callback:function(a){t.$set(t.editData,"role",a)},expression:"editData.role"}},[t._v("用户")])],1)],1)],1)],1)},staticRenderFns:[]};var c=e("VU/8")(l,r,!1,function(t){e("WrV1")},"data-v-797b0b61",null);a.default=c.exports}});
//# sourceMappingURL=20.f7860fcb71139d4fd134.js.map