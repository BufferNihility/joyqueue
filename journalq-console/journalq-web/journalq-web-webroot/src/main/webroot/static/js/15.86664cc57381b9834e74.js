/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
webpackJsonp([15,30],{"0Zbj":function(t,e){},GrpU:function(t,e){},"VKC/":function(t,e,a){"use strict";Object.defineProperty(e,"__esModule",{value:!0});var r={name:"task-form",mixins:[a("lcoF").a],props:{type:0,data:{type:Object,default:function(){return{type:"",mutex:"",referId:0,priority:0,daemons:!1,url:"",cron:"",dispatchType:0,retry:!0,status:1}}}},data:function(){return{formData:this.data,rules:{type:[{required:!0,message:"请填写类型",trigger:"change"}],referId:[{required:!0,message:"请填写关联id",trigger:"change"}],priority:[{required:!0,message:"请填优先级",trigger:"change"}],daemons:[{required:!0,message:"请填写守护线程",trigger:"change"}],dispatchType:[{required:!0,message:"请填写派发类型",trigger:"change"}],retry:[{required:!0,message:"请填写重试类型",trigger:"change"}]}}}},i={render:function(){var t=this,e=t.$createElement,a=t._self._c||e;return a("d-form",{ref:"form",staticStyle:{height:"350px","overflow-y":"auto",width:"100%","padding-right":"20px"},attrs:{model:t.formData,rules:t.rules,"label-width":"100px"}},[a("d-form-item",{staticStyle:{width:"60%"},attrs:{label:"类型:",prop:"type"}},[a("d-input",{staticStyle:{width:"249px"},attrs:{placeholder:"由字母和点组成，如Topic.Update"},model:{value:t.formData.type,callback:function(e){t.$set(t.formData,"type",e)},expression:"formData.type"}})],1),t._v(" "),a("d-form-item",{staticStyle:{width:"60%"},attrs:{label:"互斥类型:"}},[a("d-input",{staticStyle:{width:"249px"},attrs:{placeholder:"互斥任务类型,支持后缀*，如Topic.*"},model:{value:t.formData.mutex,callback:function(e){t.$set(t.formData,"mutex",e)},expression:"formData.mutex"}})],1),t._v(" "),a("d-form-item",{staticStyle:{width:"60%"},attrs:{label:"关联主键:",prop:"referId"}},[a("d-input",{staticStyle:{width:"249px"},model:{value:t.formData.referId,callback:function(e){t.$set(t.formData,"referId",e)},expression:"formData.referId"}})],1),t._v(" "),a("d-form-item",{staticStyle:{width:"60%"},attrs:{label:"优先级:",prop:"priority"}},[a("d-input",{staticStyle:{width:"249px"},model:{value:t.formData.priority,callback:function(e){t.$set(t.formData,"priority",e)},expression:"formData.priority"}})],1),t._v(" "),a("d-form-item",{attrs:{label:"守护任务:",prop:"daemons"}},[a("d-radio-group",{model:{value:t.formData.daemons,callback:function(e){t.$set(t.formData,"daemons",e)},expression:"formData.daemons"}},[a("d-radio",{attrs:{label:!0}},[t._v("是")]),t._v(" "),a("d-radio",{attrs:{label:!1}},[t._v("否")]),t._v(" "),t.formData.daemons?a("span",{staticStyle:{color:"red"}},[t._v("守护任务默认重试")]):t._e()],1)],1),t._v(" "),a("d-form-item",{staticStyle:{width:"60%"},attrs:{label:"参数:",prop:"url"}},[a("d-input",{staticStyle:{width:"249px"},attrs:{placeholder:"任务的参数，如?a=1&b=2"},model:{value:t.formData.url,callback:function(e){t.$set(t.formData,"url",e)},expression:"formData.url"}})],1),t._v(" "),a("d-form-item",{staticStyle:{width:"60%"},attrs:{label:"表达式:",prop:"cron"}},[a("d-input",{staticStyle:{width:"249px"},attrs:{placeholder:"cron表达式，如 0 5 0/1 * * ?"},model:{value:t.formData.cron,callback:function(e){t.$set(t.formData,"cron",e)},expression:"formData.cron"}})],1),t._v(" "),a("d-form-item",{attrs:{label:"派发类型:",prop:"dispatchType"}},[a("d-radio-group",{model:{value:t.formData.dispatchType,callback:function(e){t.$set(t.formData,"dispatchType",e)},expression:"formData.dispatchType"}},[a("d-radio",{attrs:{label:0}},[t._v("任意执行器")]),t._v(" "),a("d-radio",{attrs:{label:1}},[t._v("原有执行器优先 ")]),t._v(" "),a("d-radio",{attrs:{label:2}},[t._v("必须原有执行器")])],1)],1),t._v(" "),a("d-form-item",{attrs:{label:"重试:",prop:"retry"}},[a("d-radio-group",{model:{value:t.formData.retry,callback:function(e){t.$set(t.formData,"retry",e)},expression:"formData.retry"}},[a("d-radio",{attrs:{label:!0}},[t._v("是")]),t._v(" "),a("d-radio",{attrs:{label:!1}},[t._v("否")])],1)],1)],1)},staticRenderFns:[]};var o=a("VU/8")(r,i,!1,function(t){a("0Zbj")},"data-v-7bb79e8c",null);e.default=o.exports},bkdn:function(t,e,a){"use strict";Object.defineProperty(e,"__esModule",{value:!0});var r=a("woOf"),i=a.n(r),o=a("1a0f"),n=a("T0gc"),s=a("fo4W"),l=a("95hR"),d=a("VKC/"),c={name:"task",components:{myTable:n.a,myDialog:s.a,taskFrom:d.default},mixins:[l.a],data:function(){return{searchData:{keyword:"",newOrFailed:"true"},searchRules:{},deleteDialog:{visible:!1},tableData:{rowData:[],colData:[{title:"ID",key:"id"},{title:"任务类型",key:"type"},{title:"互斥类型",key:"mutex"},{title:"关联主键",key:"referId"},{title:"优先级",key:"priority"},{title:"参数",key:"url"},{title:"表达式",key:"cron"},{title:"派发类型",key:"dispatchType",formatter:function(t){return 0===t.dispatchType?"任意执行器":1===t.dispatchType?"原有执行器优先":2===t.dispatchType?"必须派发给原有的执行器":void 0}},{title:"守护任务",key:"daemons",formatter:function(t){return t.daemons?"是":"否"}},{title:"重试",key:"retry",formatter:function(t){return t.retry?"是":"否"}},{title:"重试次数",key:"retryCount"},{title:"状态",key:"status",formatter:function(t){return 1===t.status?"新建":0===t.status?"审核中":-1===t.status?"删除":2===t.status?(t.isException=!0,"失败需要重试"):3===t.status?"已派发":4===t.status?"执行中":5===t.status?"成功":6===t.status?(t.isException=!0,"失败不重试"):void 0}}],btns:[{txt:"编辑",method:"on-edit",bindKey:"status",bindVal:0},{txt:"启用",method:"on-enable",bindKey:"status",bindVal:0},{txt:"查看异常",method:"on-viewException",bindKey:"isException",bindVal:1},{txt:"删除",method:"on-del"}]},addDialog:{visible:!1,title:"新建任务",showFooter:!0},addData:{type:"",mutex:"",referId:0,priority:0,daemons:!1,url:"",cron:"",dispatchType:0,retry:!0,status:0},editDialog:{visible:!1,title:"编辑任务",showFooter:!0},editData:{},editSubmitData:{},statusEnum:[{key:-1,value:"删除"},{key:0,value:"审核中"},{key:1,value:"新增"},{key:2,value:"失败需要重试"},{key:3,value:"已派发"},{key:4,value:"执行中"},{key:5,value:"成功"},{key:6,value:"失败不重试"}]}},methods:{openDialog:function(t){this[t].visible=!0,console.log(t),"addDialog"===t&&(this.addData={type:"",mutex:"",referId:0,priority:0,daemons:!1,url:"",cron:"",dispatchType:0,retry:!0,status:0})},state:function(t,e){var a=this,r=i()({},t);r.status=e,o.a.put(this.urlOrigin.state+"/"+r.id,{},r).then(function(t){a.$Dialog.success({content:"启用成功"}),a.getList()})},enable:function(t){this.state(t,1)},viewException:function(t){this.$Dialog.confirm({title:"异常详情",content:t.exception})}},mounted:function(){this.getList()}},u={render:function(){var t=this,e=t.$createElement,a=t._self._c||e;return a("div",[a("div",{staticClass:"ml20 mt30"},[a("d-input",{staticClass:"left mr10",staticStyle:{width:"20%"},attrs:{placeholder:"请输入任务类型"},model:{value:t.searchData.keyword,callback:function(e){t.$set(t.searchData,"keyword",e)},expression:"searchData.keyword"}},[a("icon",{attrs:{slot:"suffix",name:"search",size:"14",color:"#CACACA"},on:{click:t.getList},slot:"suffix"})],1),t._v(" "),a("d-button",{attrs:{type:"primary"},on:{click:function(e){return t.openDialog("addDialog")}}},[t._v("新建任务"),a("icon",{staticStyle:{"margin-left":"5px"},attrs:{name:"plus-circle"}})],1)],1),t._v(" "),a("my-table",{attrs:{data:t.tableData,showPin:t.showTablePin,page:t.page},on:{"on-size-change":t.handleSizeChange,"on-current-change":t.handleCurrentChange,"on-selection-change":t.handleSelectionChange,"on-del":t.del,"on-enable":t.enable,"on-edit":t.edit,"on-viewException":t.viewException}}),t._v(" "),a("my-dialog",{attrs:{dialog:t.addDialog},on:{"on-dialog-confirm":function(e){return t.addConfirm()},"on-dialog-cancel":function(e){return t.addCancel()}}},[a("task-from",{ref:"addForm",attrs:{data:t.addData,type:t.$store.getters.addFormType}})],1),t._v(" "),a("my-dialog",{attrs:{dialog:t.editDialog},on:{"on-dialog-confirm":function(e){return t.editConfirm()},"on-dialog-cancel":function(e){return t.addCancel()}}},[a("task-from",{ref:"editForm",attrs:{data:t.editData,type:t.$store.getters.editFormType}})],1)],1)},staticRenderFns:[]};var p=a("VU/8")(c,u,!1,function(t){a("GrpU")},"data-v-50ba1b84",null);e.default=p.exports}});
//# sourceMappingURL=15.86664cc57381b9834e74.js.map