<template>
  <div class="asset-page">
    <div class="summary-row">
      <div class="surface summary-item">
        <span>项目</span><strong>{{ projects.length }}</strong>
      </div>
      <div class="surface summary-item">
        <span>分组</span><strong>{{ groups.length }}</strong>
      </div>
      <div class="surface summary-item">
        <span>当前设备</span><strong>{{ devices.length }}</strong>
      </div>
      <div class="surface summary-item">
        <span>当前点位</span><strong>{{ currentPoints.length }}</strong>
      </div>
    </div>

    <div class="workbench">
      <aside :class="['surface', 'tree-panel', { collapsed: treeCollapsed }]">
        <div class="tree-head">
          <div v-if="!treeCollapsed">
            <h2>资产层级</h2>
            <span>项目 / 区域 / 产线 / 站点</span>
          </div>
          <el-button :icon="treeCollapsed ? Expand : Fold" @click="treeCollapsed = !treeCollapsed" />
        </div>

        <template v-if="!treeCollapsed">
          <div class="tree-actions">
            <el-button type="primary" :icon="Plus" :disabled="!canManageProjects" @click="openProjectDialog()">项目</el-button>
            <el-button :icon="FolderAdd" :disabled="!canManageAssets || !selectedProjectId" @click="openGroupDialog()">分组</el-button>
            <el-button :icon="Refresh" @click="loadAll" />
          </div>

          <el-input v-model="treeKeyword" clearable placeholder="搜索项目或分组" />

          <el-tree
            ref="treeRef"
            class="asset-tree"
            node-key="treeKey"
            :data="filteredTree"
            :props="{ label: 'label', children: 'children' }"
            :expand-on-click-node="false"
            default-expand-all
            highlight-current
            @node-click="selectTreeNode"
          >
            <template #default="{ data }">
              <div :class="['tree-node', data.nodeType === 'GROUP' ? groupTypeClass(data.groupType) : 'project-node']">
                <el-icon v-if="data.nodeType === 'PROJECT'" class="node-icon project-icon">
                  <OfficeBuilding />
                </el-icon>
                <span v-else :class="['group-kind-icon', groupTypeClass(data.groupType)]">
                  {{ groupTypeShort(data.groupType) }}
                </span>
                <span class="tree-label">{{ data.label }}</span>
                <span
                  v-if="data.nodeType === 'GROUP'"
                  class="tree-type"
                >
                  {{ groupTypeLabel(data.groupType) }}
                </span>
                <span
                  v-else
                  :class="['tree-type', 'permission-badge', permissionClass(projectPermissionOf(data.projectId))]"
                >
                  {{ projectPermissionLabel(data.projectId) }}
                </span>
                <span v-if="data.nodeType === 'GROUP'" class="tree-count">{{ data.deviceCount || 0 }}</span>
                <el-dropdown v-if="canManageTreeNode(data)" trigger="click" @command="(command) => handleTreeCommand(command, data)">
                  <el-button link :icon="MoreFilled" @click.stop />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item v-if="data.nodeType === 'PROJECT'" command="workOrderPolicy">工单流程设置</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-tree>
        </template>
      </aside>

      <section class="surface main-panel">
        <div class="panel-breadcrumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>资产</el-breadcrumb-item>
            <el-breadcrumb-item>{{ selectedNode?.label || '全部项目' }}</el-breadcrumb-item>
          </el-breadcrumb>
          <span class="permission"><span class="status-dot ok"></span>权限：{{ currentPermissionLabel }}</span>
        </div>

        <div class="table-toolbar">
          <div class="filter-row">
            <el-select v-model="filters.protocolType" clearable placeholder="协议" style="width: 150px">
              <el-option v-for="item in protocols" :key="item.protocolType" :label="item.displayName || item.protocolType" :value="item.protocolType" />
            </el-select>
            <el-input v-model="filters.keyword" clearable placeholder="过滤设备名称或 IP" style="width: 240px" @keyup.enter="loadDevices" />
            <el-button :icon="Search" @click="loadDevices">查询</el-button>
          </div>
          <div class="filter-row">
            <el-button :icon="FolderAdd" :disabled="!canManageAssets || !selectedProjectId" @click="openGroupDialog()">新增分组</el-button>
            <el-button type="primary" :icon="Plus" :disabled="!canManageAssets || !selectedProjectId" @click="openDeviceDialog()">新增设备</el-button>
            <el-button :icon="Upload" :disabled="!canManageAssets" @click="openFullImportDialog">完整导入</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="devices" height="100%" highlight-current-row class="device-table" @row-click="selectDevice">
          <el-table-column prop="deviceName" label="设备名称" min-width="180" sortable show-overflow-tooltip align="center" header-align="center" />
          <el-table-column prop="deviceType" label="设备类型" min-width="120" align="center" header-align="center" />
          <el-table-column prop="protocolType" label="协议" min-width="130" align="center" header-align="center">
            <template #default="{ row }">
              <el-tag effect="plain">{{ row.protocolType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="连接地址" min-width="170" align="center" header-align="center">
            <template #default="{ row }"><span class="mono">{{ row.ipAddress || '-' }}:{{ row.port || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" min-width="130" align="center" header-align="center">
            <template #default="{ row }">
              <div class="status-cell">
                <span>
                  <span :class="['status-dot', statusClass(row.status)]"></span>
                  <span class="status-text">{{ statusLabel(row.status) }}</span>
                </span>
                <el-tooltip v-if="hasPointCollectAlarm(row)" :content="row.activePointAlarmSummary || '存在点位采集异常'" placement="top">
                  <el-tag size="small" type="warning" effect="plain">点位异常 {{ row.activePointAlarmCount }}</el-tag>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="采集周期" min-width="110" align="center" header-align="center">
            <template #default="{ row }">{{ intervalLabel(row.collectIntervalMs) }}</template>
          </el-table-column>
          <el-table-column label="最后采集" min-width="170" align="center" header-align="center">
            <template #default="{ row }">{{ formatTime(row.lastCollectTime) }}</template>
          </el-table-column>
          <el-table-column prop="lastErrorMessage" label="错误信息" min-width="180" show-overflow-tooltip align="center" header-align="center" />
          <el-table-column prop="remark" label="描述" min-width="180" show-overflow-tooltip align="center" header-align="center" />
          <el-table-column label="操作" min-width="320" fixed="right" class-name="action-column" align="center" header-align="center">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button size="small" :icon="DataLine" @click.stop="openPoints(row, 'runtime')">实时</el-button>
                <el-button size="small" :icon="Grid" @click.stop="openPoints(row, 'config')">点表</el-button>
                <el-button v-if="canManageAssets" size="small" :icon="Edit" @click.stop="openDeviceDialog(row)">编辑</el-button>
                <el-button v-if="canManageAssets" size="small" type="danger" :icon="Delete" @click.stop="removeDevice(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-drawer v-model="pointDrawerVisible" size="88%" :with-header="false" destroy-on-close>
      <div class="point-drawer">
        <div class="point-head">
          <div>
            <h2>{{ activeDevice?.deviceName || '点位数据' }}</h2>
            <span>{{ activeDevice?.protocolType }} / {{ activeDevice?.ipAddress }}:{{ activeDevice?.port }}</span>
          </div>
          <div class="filter-row">
            <el-input v-model="pointKeyword" clearable placeholder="过滤点位名称或数据标识" style="width: 260px" />
            <el-select v-model="pointTypeFilter" clearable placeholder="类型" style="width: 130px">
              <el-option label="Boolean" value="Boolean" />
              <el-option label="Int16" value="Int16" />
              <el-option label="UInt16" value="UInt16" />
              <el-option label="Int32" value="Int32" />
              <el-option label="UInt32" value="UInt32" />
              <el-option label="Float32" value="Float32" />
              <el-option label="String" value="String" />
            </el-select>
            <el-switch v-model="writeOnly" active-text="只看可写" />
            <el-button :icon="Refresh" @click="refreshPointDrawer">刷新</el-button>
            <el-button v-if="activeProtocolSupports('supportsDiscovery')" :icon="Search" :disabled="!canManageAssets" @click="discoverProtocolPoints">发现点位</el-button>
            <el-button type="primary" :icon="Plus" :disabled="!canManageAssets" @click="openPointDialog()">新增点位</el-button>
            <el-button v-if="activeProtocolSupports('supportsExcelImport')" :icon="Download" @click="downloadPointTemplate">下载模板</el-button>
            <el-upload v-if="activeProtocolSupports('supportsExcelImport')" :show-file-list="false" accept=".xlsx,.xls" :disabled="!canManageAssets" :before-upload="importPointExcel">
              <el-button :icon="Upload" :disabled="!canManageAssets">Excel导入</el-button>
            </el-upload>
            <el-button v-if="activeProtocolSupports('supportsJsonImport')" :icon="Upload" :disabled="!canManageAssets" @click="openBatchPointDialog">JSON导入</el-button>
            <el-button
              v-if="activePointTab === 'config' && canManageAssets"
              type="danger"
              plain
              :icon="Delete"
              :disabled="selectedPointRows.length === 0"
              @click="removeSelectedPoints"
            >
              批量删除{{ selectedPointRows.length ? `(${selectedPointRows.length})` : '' }}
            </el-button>
          </div>
        </div>

        <el-tabs v-model="activePointTab" class="point-tabs">
          <el-tab-pane label="实时数据" name="runtime">
            <el-table :data="filteredRuntimePoints" height="calc(100vh - 206px)" class="compact-table">
              <el-table-column prop="point.pointLabel" label="点位名称" min-width="160" sortable />
              <el-table-column prop="point.pointKey" label="数据标识" min-width="160">
                <template #default="{ row }"><span class="mono">{{ row.point.pointKey }}</span></template>
              </el-table-column>
              <el-table-column prop="point.dataType" label="类型" width="110" />
              <el-table-column label="当前值" min-width="140">
                <template #default="{ row }">
                  <strong class="runtime-value">{{ formatRuntimeValue(row) }}</strong>
                  <span class="muted">{{ row.point.unit || '' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="采集状态" min-width="180">
                <template #default="{ row }">
                  <el-tooltip v-if="row.collectStatus === 'ERROR'" :content="row.collectErrorMessage || '点位采集异常'" placement="top">
                    <el-tag type="danger" effect="plain">异常</el-tag>
                  </el-tooltip>
                  <el-tag v-else-if="row.collectStatus === 'NO_DATA'" type="info" effect="plain">暂无数据</el-tag>
                  <el-tag v-else type="success" effect="plain">正常</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
              </el-table-column>
              <el-table-column label="读写" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.writable ? 'warning' : 'info'" effect="plain">{{ row.writable ? '读写' : '只读' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" :disabled="!canWriteRuntimePoint(row)" :icon="Edit" @click="openWriteDialog(row)">写入</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="点表配置" name="config">
            <el-table
              :data="filteredPoints"
              height="calc(100vh - 206px)"
              row-key="id"
              class="compact-table"
              @selection-change="handlePointSelectionChange"
            >
              <el-table-column v-if="canManageAssets" type="selection" width="52" align="center" />
              <el-table-column prop="pointLabel" label="点位名称" min-width="150" sortable />
              <el-table-column prop="pointKey" label="数据标识" min-width="150">
                <template #default="{ row }"><span class="mono">{{ row.pointKey }}</span></template>
              </el-table-column>
              <el-table-column prop="address" label="地址" width="100" />
              <el-table-column v-if="isActiveModbus" prop="functionCode" label="FC" width="70" />
              <el-table-column v-if="isActiveModbus" prop="slaveId" label="从站" width="80" />
              <el-table-column prop="dataType" label="类型" width="110" />
              <el-table-column prop="coef" label="倍率" width="90" />
              <el-table-column prop="decimalPlaces" label="小数位" width="90" />
              <el-table-column prop="unit" label="单位" width="90" />
              <el-table-column label="读写" width="100">
                <template #default="{ row }">
                  <el-tag :type="isWritable(row) ? 'warning' : 'info'" effect="plain">{{ isWritable(row) ? '读写' : '只读' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="启用" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '是' : '否' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="250" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="canManageAssets" size="small" :icon="Bell" @click="openAlarmRules(row)">报警</el-button>
                  <el-button v-if="canManageAssets" size="small" :icon="Edit" @click="openPointDialog(row)">编辑</el-button>
                  <el-button v-if="canManageAssets" size="small" type="danger" :icon="Delete" @click="removePoint(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="projectDialogVisible" :title="projectForm.id ? '编辑项目' : '新增项目'" width="560px">
      <el-form :model="projectForm" label-width="92px">
        <el-form-item label="项目名称"><el-input v-model="projectForm.projectName" /></el-form-item>
        <el-form-item label="项目编码"><el-input v-model="projectForm.projectCode" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="projectForm.ownerName" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="projectForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="PAUSED" value="PAUSED" />
            <el-option label="ARCHIVED" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="projectForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="projectDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProject">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="workOrderPolicyVisible" title="工单流程设置" width="700px">
      <div class="policy-title">
        <strong>{{ workOrderPolicyProjectName }}</strong>
        <span>该设置属于项目级流程规则，会影响该项目下所有报警转工单和手工工单。</span>
      </div>
      <div class="policy-grid">
        <div class="policy-item">
          <strong>派单人可作为处理人</strong>
          <span>默认关闭，避免自己派给自己。</span>
          <el-switch v-model="workOrderPolicyForm.allowDispatcherAsAssignee" />
        </div>
        <div class="policy-item">
          <strong>派单人可作为验收人</strong>
          <span>默认开启，项目管理员可派单后验收。</span>
          <el-switch v-model="workOrderPolicyForm.allowDispatcherAsVerifier" />
        </div>
        <div class="policy-item">
          <strong>处理人可自验</strong>
          <span>默认关闭，避免自己处理自己验收。</span>
          <el-switch v-model="workOrderPolicyForm.allowAssigneeVerifySelf" />
        </div>
        <div class="policy-item">
          <strong>验收通过自动关闭</strong>
          <span>关闭后需要项目管理员手动关闭。</span>
          <el-switch v-model="workOrderPolicyForm.autoCloseAfterVerify" />
        </div>
        <div class="policy-item">
          <strong>关闭后自动归档</strong>
          <span>生成维修资料卡，用于后续推荐。</span>
          <el-switch v-model="workOrderPolicyForm.autoArchiveAfterClose" />
        </div>
        <div class="policy-item">
          <strong>要求上传处理照片</strong>
          <span>开启后完成处理前必须上传图片附件。</span>
          <el-switch v-model="workOrderPolicyForm.requireProcessPhoto" />
        </div>
        <div class="policy-item">
          <strong>必须填写故障原因</strong>
          <span>用于沉淀维修知识。</span>
          <el-switch v-model="workOrderPolicyForm.requireFaultReason" />
        </div>
        <div class="policy-item">
          <strong>必须填写处理措施</strong>
          <span>用于形成可复用维修步骤。</span>
          <el-switch v-model="workOrderPolicyForm.requireProcessMeasure" />
        </div>
      </div>
      <template #footer>
        <el-button @click="workOrderPolicyVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWorkOrderPolicy">保存设置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupDialogVisible" :title="groupForm.id ? '编辑分组' : '新增分组'" width="560px">
      <el-form :model="groupForm" label-width="92px">
        <el-form-item label="所属项目">
          <el-select v-model="groupForm.projectId" style="width: 100%" @change="groupForm.parentId = 0">
            <el-option v-for="item in projects" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上级分组">
          <el-select v-model="groupForm.parentId" style="width: 100%">
            <el-option label="项目根节点" :value="0" />
            <el-option v-for="item in groupsForProject(groupForm.projectId, groupForm.id)" :key="item.id" :label="item.groupName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组名称"><el-input v-model="groupForm.groupName" /></el-form-item>
        <el-form-item label="分组类型">
          <el-select v-model="groupForm.groupType" style="width: 100%">
            <el-option label="区域" value="AREA" />
            <el-option label="产线" value="LINE" />
            <el-option label="站点" value="STATION" />
            <el-option label="系统" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="groupForm.sortNo" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="groupForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deviceDialogVisible" :title="deviceForm.id ? '编辑设备' : '新增设备'" width="760px" class="device-dialog">
      <el-form :model="deviceForm" label-width="108px" class="device-form">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="设备名称"><el-input v-model="deviceForm.deviceName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="设备类型"><el-input v-model="deviceForm.deviceType" placeholder="PLC / RTU / Meter" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所属项目"><el-select v-model="deviceForm.projectId" style="width: 100%"><el-option v-for="item in projects" :key="item.id" :label="item.projectName" :value="item.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所属分组"><el-select v-model="deviceForm.groupId" style="width: 100%"><el-option v-for="item in groupsForProject(deviceForm.projectId)" :key="item.id" :label="item.groupName" :value="item.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="协议"><el-select v-model="deviceForm.protocolType" style="width: 100%" @change="handleProtocolChange"><el-option v-for="item in protocols" :key="item.protocolType" :label="protocolOptionLabel(item)" :value="item.protocolType" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="采集状态"><el-input :model-value="statusLabel(deviceForm.status)" disabled placeholder="保存后由采集任务自动更新" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="采集周期"><el-input-number v-model="deviceForm.collectIntervalMs" :min="1000" :step="1000" style="width: 100%" /><span class="form-tip">毫秒，默认 1000</span></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="历史存储"><el-switch v-model="deviceForm.historyEnabled" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="存储方式"><el-select v-model="deviceForm.historyMode" :disabled="!deviceForm.historyEnabled" style="width: 100%"><el-option label="周期存储" value="INTERVAL" /><el-option label="变化存储" value="CHANGE" /><el-option label="周期+变化" value="INTERVAL_CHANGE" /><el-option label="不存储" value="DISABLED" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="存储周期"><el-input-number v-model="deviceForm.historyIntervalMs" :min="1000" :step="60000" :disabled="!deviceForm.historyEnabled" style="width: 100%" /><span class="form-tip">毫秒，默认 300000</span></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="变化阈值"><el-input-number v-model="deviceForm.changeThreshold" :step="0.1" :disabled="!deviceForm.historyEnabled" style="width: 100%" /></el-form-item></el-col>
          <el-col v-for="field in selectedProtocolDeviceBaseFields" :key="field.key" :span="fieldSpan(field)">
            <el-form-item :label="field.label">
              <el-input-number v-if="field.type === 'number'" v-model="deviceForm[field.key]" :min="0" style="width: 100%" />
              <el-input v-else-if="field.type === 'textarea'" v-model="deviceForm[field.key]" type="textarea" :rows="3" :placeholder="field.placeholder" />
              <el-input v-else v-model="deviceForm[field.key]" :placeholder="field.placeholder" />
            </el-form-item>
          </el-col>
          <el-col v-for="field in selectedProtocolExtBaseFields" :key="field.key" :span="fieldSpan(field)">
            <el-form-item :label="field.label">
              <el-input-number v-if="field.type === 'number'" v-model="deviceExtConfig[field.key]" :min="0" style="width: 100%" />
              <el-input v-else-if="field.type === 'textarea'" v-model="deviceExtConfig[field.key]" type="textarea" :rows="3" :placeholder="field.placeholder" />
              <el-select v-else-if="field.type === 'select'" v-model="deviceExtConfig[field.key]" style="width: 100%">
                <el-option v-for="option in field.options || []" :key="String(option.value)" :label="option.label" :value="option.value" />
              </el-select>
              <el-switch v-else-if="field.type === 'switch'" v-model="deviceExtConfig[field.key]" />
              <el-input v-else v-model="deviceExtConfig[field.key]" :placeholder="field.placeholder" />
            </el-form-item>
          </el-col>
          <el-col v-if="selectedProtocolAdvancedFields.length" :span="24">
            <el-collapse v-model="advancedProtocolPanels" class="advanced-protocol">
              <el-collapse-item title="高级协议参数" name="advanced">
                <div v-for="group in selectedProtocolAdvancedGroups" :key="group.name" class="advanced-field-group">
                  <div class="advanced-field-group-title">{{ group.name }}</div>
                  <el-row :gutter="12">
                    <el-col v-for="field in group.fields" :key="field.target + '-' + field.key" :span="fieldSpan(field)">
                      <el-form-item :label="field.label">
                        <el-input-number v-if="field.type === 'number' && field.target === 'DEVICE'" v-model="deviceForm[field.key]" :min="0" style="width: 100%" />
                        <el-input-number v-else-if="field.type === 'number'" v-model="deviceExtConfig[field.key]" :min="0" style="width: 100%" />
                        <el-input v-else-if="field.type === 'textarea' && field.target === 'DEVICE'" v-model="deviceForm[field.key]" type="textarea" :rows="3" :placeholder="field.placeholder" />
                        <el-input v-else-if="field.type === 'textarea'" v-model="deviceExtConfig[field.key]" type="textarea" :rows="3" :placeholder="field.placeholder" />
                        <el-select v-else-if="field.type === 'select' && field.target === 'DEVICE'" v-model="deviceForm[field.key]" style="width: 100%">
                          <el-option v-for="option in field.options || []" :key="String(option.value)" :label="option.label" :value="option.value" />
                        </el-select>
                        <el-select v-else-if="field.type === 'select'" v-model="deviceExtConfig[field.key]" style="width: 100%">
                          <el-option v-for="option in field.options || []" :key="String(option.value)" :label="option.label" :value="option.value" />
                        </el-select>
                        <el-switch v-else-if="field.type === 'switch' && field.target === 'DEVICE'" v-model="deviceForm[field.key]" />
                        <el-switch v-else-if="field.type === 'switch'" v-model="deviceExtConfig[field.key]" />
                        <el-input v-else-if="field.target === 'DEVICE'" v-model="deviceForm[field.key]" :placeholder="field.placeholder" />
                        <el-input v-else v-model="deviceExtConfig[field.key]" :placeholder="field.placeholder" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </div>
              </el-collapse-item>
            </el-collapse>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="deviceForm.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDevice">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pointDialogVisible" :title="pointForm.id ? '编辑点位' : '新增点位'" width="760px">
      <el-form :model="pointForm" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="点位名称"><el-input v-model="pointForm.pointLabel" placeholder="温度 / 湿度 / 启停状态" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="数据标识"><el-input v-model="pointForm.pointKey" :placeholder="isActiveMqtt ? 'temperature / motor.speed' : 'temperature'" /></el-form-item></el-col>
          <el-col :span="isActiveModbus ? 8 : 12"><el-form-item :label="pointAddressLabel"><el-input v-model="pointForm.address" :placeholder="pointAddressPlaceholder" /></el-form-item></el-col>
          <el-col v-if="isActiveModbus" :span="8"><el-form-item label="功能码"><el-input-number v-model="pointForm.functionCode" :min="1" :max="16" /></el-form-item></el-col>
          <el-col v-if="isActiveModbus" :span="8"><el-form-item label="从站"><el-input-number v-model="pointForm.slaveId" :min="1" /></el-form-item></el-col>
          <el-col v-if="isActiveModbus" :span="8"><el-form-item label="长度"><el-input-number v-model="pointForm.quantity" :min="1" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="pointForm.dataType" @change="handlePointDataTypeChange"><el-option label="Boolean" value="Boolean" /><el-option label="Int16" value="Int16" /><el-option label="UInt16" value="UInt16" /><el-option label="Int32" value="Int32" /><el-option label="UInt32" value="UInt32" /><el-option label="Float32" value="Float32" /><el-option label="String" value="String" /></el-select></el-form-item></el-col>
          <el-col v-if="isActiveModbus" :span="8"><el-form-item label="字节序"><el-select v-model="pointForm.byteOrder"><el-option label="ABCD 标准" value="ABCD" /><el-option label="BADC 字节互换" value="BADC" /><el-option label="CDAB 寄存器互换" value="CDAB" /><el-option label="DCBA 全互换" value="DCBA" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="倍率"><el-input-number v-model="pointForm.coef" :step="0.1" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="小数位数"><el-input-number v-model="pointForm.decimalPlaces" :min="0" :max="6" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单位"><el-input v-model="pointForm.unit" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="启用"><el-switch v-model="pointForm.enabled" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="读写权限"><el-select v-model="pointForm.accessMode"><el-option label="只读" value="READ_ONLY" /><el-option label="读写" value="READ_WRITE" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="历史存储"><el-switch v-model="pointForm.historyEnabled" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="存储方式"><el-select v-model="pointForm.historyMode" :disabled="!pointForm.historyEnabled"><el-option label="继承设备" value="INHERIT" /><el-option label="周期存储" value="INTERVAL" /><el-option label="变化存储" value="CHANGE" /><el-option label="周期+变化" value="INTERVAL_CHANGE" /><el-option label="不存储" value="DISABLED" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="存储周期"><el-input-number v-model="pointForm.historyIntervalMs" :min="1000" :step="60000" :disabled="!pointForm.historyEnabled" style="width: 100%" /><span class="form-tip">毫秒，默认 300000</span></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="变化阈值"><el-input-number v-model="pointForm.changeThreshold" :step="0.1" :disabled="!pointForm.historyEnabled" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="点位描述"><el-input v-model="pointForm.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="pointDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePoint">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="alarmRuleDialogVisible" :title="`${alarmRuleTarget?.pointLabel || '点位'} · 报警规则`" width="980px" destroy-on-close>
      <div class="alarm-rule-main">
        <div class="alarm-rule-toolbar">
          <div class="alarm-rule-title">
            <strong>{{ alarmRuleTarget?.pointLabel }}</strong>
            <span class="mono">{{ alarmRuleTarget?.pointKey }}</span>
            <span>{{ alarmRuleTarget?.dataType }}</span>
            <span v-if="alarmRuleTarget?.unit">{{ alarmRuleTarget.unit }}</span>
            <el-tag effect="plain" size="small">{{ alarmRules.length }} 条</el-tag>
          </div>
          <el-button type="primary" :icon="Plus" @click="openAlarmRuleForm()">新增规则</el-button>
        </div>
        <el-table :data="alarmRules" height="430px" class="compact-table alarm-table">
            <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="级别" width="96" align="center">
              <template #default="{ row }">
                <el-tag :type="severityType(row.severity)" effect="plain">{{ severityLabel(row.severity) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="报警条件" min-width="180" align="center">
              <template #default="{ row }">{{ conditionText(row) }}</template>
            </el-table-column>
            <el-table-column label="触发条件" min-width="150" align="center">
              <template #default="{ row }">
                {{ row.immediateAlarm ? '立即触发' : `连续满足 ${msLabel(row.triggerDurationMs)}` }}
              </template>
            </el-table-column>
            <el-table-column label="恢复条件" min-width="150" align="center">
              <template #default="{ row }">连续恢复 {{ msLabel(row.recoverDurationMs) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="86" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="168" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :icon="Edit" @click="openAlarmRuleForm(row)">编辑</el-button>
                <el-button size="small" type="danger" :icon="Delete" @click="removeAlarmRule(row)">删除</el-button>
              </template>
            </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="alarmRuleFormVisible" :title="alarmRuleForm.id ? '编辑报警规则' : '新增报警规则'" width="760px" destroy-on-close>
      <el-form :model="alarmRuleForm" label-width="104px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规则名称"><el-input v-model="alarmRuleForm.ruleName" placeholder="高温报警" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="报警级别"><el-select v-model="alarmRuleForm.severity"><el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="判断条件"><el-select v-model="alarmRuleForm.conditionType"><el-option v-for="item in conditionOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="启用"><el-switch v-model="alarmRuleForm.enabled" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="isRangeCondition(alarmRuleForm.conditionType) ? '阈值下限' : '报警阈值'"><el-input-number v-model="alarmRuleForm.thresholdValue" :precision="pointDecimalPlaces" :step="0.1" style="width: 100%" /></el-form-item></el-col>
          <el-col v-if="isRangeCondition(alarmRuleForm.conditionType)" :span="12"><el-form-item label="阈值上限"><el-input-number v-model="alarmRuleForm.thresholdHigh" :precision="pointDecimalPlaces" :step="0.1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="触发方式">
              <el-radio-group v-model="alarmRuleForm.immediateAlarm">
                <el-radio-button :label="false">防抖</el-radio-button>
                <el-radio-button :label="true">立即</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="报警延时"><el-input-number v-model="alarmRuleForm.triggerDurationMs" :min="0" :step="1000" :disabled="alarmRuleForm.immediateAlarm" style="width: 100%" /><span class="form-tip">毫秒</span></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="恢复延时"><el-input-number v-model="alarmRuleForm.recoverDurationMs" :min="0" :step="1000" style="width: 100%" /><span class="form-tip">毫秒</span></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="恢复条件"><el-input model-value="报警条件不成立" disabled /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="alarmRuleForm.remark" type="textarea" :rows="3" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="alarmRuleFormVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAlarmRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="writeDialogVisible" title="写入点位值" width="460px">
      <el-form label-width="92px">
        <el-form-item label="点位名称">{{ writeTarget?.point?.pointLabel }}</el-form-item>
        <el-form-item label="数据类型">{{ writeTarget?.point?.dataType }}</el-form-item>
        <el-form-item label="写入值">
          <el-select v-if="writeTarget?.point?.dataType === 'Boolean'" v-model="writeValue" style="width: 100%">
            <el-option label="0 / 关" :value="0" />
            <el-option label="1 / 开" :value="1" />
          </el-select>
          <el-input-number v-else v-model="writeValue" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="writeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWritePoint">写入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchPointVisible" title="批量点表 JSON" width="780px">
      <div class="json-template-head">
        <div>
          <strong>{{ activeDevice?.protocolType || '-' }} 点表模板</strong>
          <span>{{ activePointTemplateHint }}</span>
        </div>
        <el-button size="small" @click="resetBatchPointTemplate">重置当前协议模板</el-button>
      </div>
      <el-input v-model="batchPointJson" type="textarea" :rows="16" class="mono" />
      <template #footer>
        <el-button @click="batchPointVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBatchPoints">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="opcBrowseVisible" title="OPC UA 点位发现" width="980px" class="opc-browse-dialog">
      <div class="opc-browse-layout">
        <section class="opc-tree-panel">
          <div class="opc-panel-head">
            <strong>地址空间</strong>
            <span>只选择变量节点导入点表</span>
          </div>
          <el-tree
            ref="opcBrowseTreeRef"
            :data="opcBrowseTree"
            node-key="id"
            show-checkbox
            default-expand-all
            :props="opcTreeProps"
            :check-strictly="true"
            :filter-node-method="filterOpcNode"
            @check="handleOpcCheck"
          >
            <template #default="{ data }">
              <div class="opc-node-row">
                <span :class="['opc-node-name', data.variable ? 'variable' : 'folder']">{{ data.label }}</span>
                <el-tag v-if="data.variable" size="small" effect="plain">{{ data.dataType || 'String' }}</el-tag>
                <span v-if="data.variable" class="opc-node-id">{{ data.nodeId }}</span>
              </div>
            </template>
          </el-tree>
        </section>
        <section class="opc-selected-panel">
          <div class="opc-panel-head">
            <strong>已选择 {{ selectedOpcNodes.length }}</strong>
            <el-button size="small" @click="clearOpcSelection">清空</el-button>
          </div>
          <div class="opc-selected-list">
            <div v-for="node in selectedOpcNodes" :key="node.id" class="opc-selected-item">
              <strong>{{ node.label }}</strong>
              <span>{{ node.nodeId }}</span>
              <em>{{ node.sampleValue ?? '-' }}</em>
            </div>
            <div v-if="selectedOpcNodes.length === 0" class="opc-empty">请选择变量节点</div>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="opcBrowseVisible = false">取消</el-button>
        <el-button type="primary" :disabled="selectedOpcNodes.length === 0" @click="importSelectedOpcNodes">导入所选点位</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importPreviewVisible"
      :title="previewSource === 'DISCOVERY' ? '发现点位确认' : 'Excel 点表导入确认'"
      width="1280px"
      class="import-preview-dialog"
    >
      <div v-if="importPreview" class="import-preview-head">
        <div class="import-summary">
          <el-tag>总行数 {{ importPreview.totalCount }}</el-tag>
          <el-tag type="success">有效 {{ importPreview.validCount }}</el-tag>
          <el-tag type="warning">重复 {{ importPreview.duplicateCount }}</el-tag>
          <el-tag type="danger">错误 {{ importPreview.invalidCount }}</el-tag>
          <el-tag type="info">已选 {{ selectedImportCount }}</el-tag>
        </div>
        <el-radio-group v-model="duplicateStrategy" size="small">
          <el-radio-button label="SKIP">跳过重复</el-radio-button>
          <el-radio-button label="OVERWRITE">覆盖重复</el-radio-button>
          <el-radio-button label="CREATE">全部新增</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="importPreview" class="import-preview-tools">
        <el-radio-group v-model="importPreviewFilter" size="small">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="SELECTED">已选</el-radio-button>
          <el-radio-button label="VALID">可新增</el-radio-button>
          <el-radio-button label="DUPLICATE">重复</el-radio-button>
          <el-radio-button label="ERROR">错误</el-radio-button>
        </el-radio-group>
        <div class="filter-row">
          <el-button size="small" @click="setImportSelection('ALL')">全选有效</el-button>
          <el-button size="small" @click="setImportSelection('NEW_ONLY')">只选新增</el-button>
          <el-button size="small" @click="setImportSelection('NONE')">清空选择</el-button>
        </div>
      </div>

      <div v-if="importPreview" class="import-batch-panel">
        <span class="batch-title">批量设置已选点位</span>
        <el-select v-model="importBatchAccessMode" clearable placeholder="读写权限不变" style="width: 150px">
          <el-option label="只读" value="READ_ONLY" />
          <el-option label="读写" value="READ_WRITE" />
        </el-select>
        <el-input v-model="importBatchUnit" clearable placeholder="单位不变" style="width: 140px" />
        <el-input-number v-model="importBatchDecimalPlaces" :min="0" :max="6" placeholder="小数位" style="width: 130px" />
        <el-button size="small" type="primary" plain @click="applyImportBatch">应用</el-button>
        <el-button size="small" @click="resetImportBatch">清空</el-button>
      </div>

      <el-table :data="importPreviewRows" height="430px" class="compact-table import-preview-table">
        <el-table-column label="选择" width="70" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.selected" :disabled="!row.valid" />
          </template>
        </el-table-column>
        <el-table-column prop="rowNumber" label="行号" width="70" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="!row.valid" type="danger">错误</el-tag>
            <el-tag v-else-if="row.duplicate && duplicateStrategy === 'SKIP'" type="warning">将跳过</el-tag>
            <el-tag v-else-if="row.duplicate && duplicateStrategy === 'OVERWRITE'" type="warning">将覆盖</el-tag>
            <el-tag v-else-if="row.duplicate" type="warning">重复新增</el-tag>
            <el-tag v-else type="success">可导入</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="点位名称" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input v-if="row.point" v-model="row.point.pointLabel" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="point.pointKey" label="数据标识" min-width="160" show-overflow-tooltip />
        <el-table-column prop="point.address" label="地址" width="100" />
        <el-table-column v-if="previewSource === 'DISCOVERY'" prop="sampleValue" label="样例值" min-width="130" show-overflow-tooltip />
        <el-table-column v-if="isActiveModbus" prop="point.functionCode" label="FC" width="70" align="center" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-select v-if="row.point" v-model="row.point.dataType" size="small">
              <el-option v-for="item in pointDataTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="小数位" width="110" align="center">
          <template #default="{ row }">
            <el-input-number v-if="row.point" v-model="row.point.decimalPlaces" size="small" :min="0" :max="6" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="120">
          <template #default="{ row }">
            <el-input v-if="row.point" v-model="row.point.unit" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="point.accessMode" label="读写" width="120">
          <template #default="{ row }">
            <el-select v-if="row.point" v-model="row.point.accessMode" size="small">
              <el-option label="只读" value="READ_ONLY" />
              <el-option label="读写" value="READ_WRITE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="问题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.errors || []).join('；') }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="import-footer-info">将导入 {{ selectedImportCount }} 个有效点位</span>
        <el-button @click="importPreviewVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!importPreview || selectedImportCount === 0" @click="confirmPointImport">
          确认导入
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fullImportVisible" title="完整设备配置 JSON" width="840px">
      <div class="json-template-head">
        <div>
          <strong>完整导入模板</strong>
          <span>按协议生成设备连接参数和点表字段，避免把 Modbus 字段套到 MQTT / OPC UA。</span>
        </div>
        <div class="json-template-actions">
          <el-select v-model="fullImportProtocol" size="small" style="width: 180px" @change="resetFullConfigTemplate">
            <el-option v-for="item in protocols" :key="item.protocolType" :label="protocolOptionLabel(item)" :value="item.protocolType" />
          </el-select>
          <el-button size="small" @click="resetFullConfigTemplate">重置模板</el-button>
        </div>
      </div>
      <el-input v-model="fullConfigJson" type="textarea" :rows="18" class="mono" />
      <template #footer>
        <el-button @click="fullImportVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFullImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bell,
  DataLine,
  Delete,
  Download,
  Edit,
  Expand,
  FolderAdd,
  Fold,
  Grid,
  MoreFilled,
  OfficeBuilding,
  Plus,
  Refresh,
  Search,
  Upload
} from '@element-plus/icons-vue'
import { api } from '../api'

const emit = defineEmits(['api-state'])
const treeRef = ref(null)
const loading = ref(false)
const treeData = ref([])
const projects = ref([])
const groups = ref([])
const protocols = ref([])
const devices = ref([])
const currentPoints = ref([])
const runtimePoints = ref([])
const selectedPointRows = ref([])
const activeDevice = ref(null)
const selectedNode = ref(null)
const treeKeyword = ref('')
const pointKeyword = ref('')
const pointTypeFilter = ref('')
const writeOnly = ref(false)
const treeCollapsed = ref(false)
const activePointTab = ref('runtime')

const projectDialogVisible = ref(false)
const workOrderPolicyVisible = ref(false)
const groupDialogVisible = ref(false)
const deviceDialogVisible = ref(false)
const pointDialogVisible = ref(false)
const pointDrawerVisible = ref(false)
const batchPointVisible = ref(false)
const fullImportVisible = ref(false)
const writeDialogVisible = ref(false)
const writeTarget = ref(null)
const writeValue = ref(0)
const currentUser = ref(null)
const importPreviewVisible = ref(false)
const importPreview = ref(null)
const duplicateStrategy = ref('SKIP')
const previewSource = ref('EXCEL')
const importPreviewFilter = ref('ALL')
const importBatchAccessMode = ref('')
const importBatchUnit = ref('')
const importBatchDecimalPlaces = ref(null)
const opcBrowseVisible = ref(false)
const opcBrowseTree = ref([])
const opcBrowseTreeRef = ref(null)
const selectedOpcNodes = ref([])
const alarmRuleDialogVisible = ref(false)
const alarmRuleFormVisible = ref(false)
const alarmRuleTarget = ref(null)
const alarmRules = ref([])
let runtimeRefreshTimer = null
let deviceRefreshTimer = null

const filters = reactive({ keyword: '', protocolType: '' })
const projectForm = reactive({})
const workOrderPolicyProjectId = ref(null)
const workOrderPolicyProjectName = ref('')
const workOrderPolicyForm = reactive({
  autoCreateFromAlarm: false,
  allowDispatcherAsAssignee: false,
  allowDispatcherAsVerifier: true,
  allowAssigneeVerifySelf: false,
  autoCloseAfterVerify: true,
  autoArchiveAfterClose: true,
  requireProcessPhoto: false,
  requireFaultReason: true,
  requireProcessMeasure: true
})
const groupForm = reactive({})
const deviceForm = reactive({})
const deviceExtConfig = reactive({})
const advancedProtocolPanels = ref([])
const pointForm = reactive({})
const alarmRuleForm = reactive({})

const severityOptions = [
  { label: '提示', value: 'INFO' },
  { label: '预警', value: 'WARN' },
  { label: '一般', value: 'MINOR' },
  { label: '严重', value: 'MAJOR' },
  { label: '紧急', value: 'CRITICAL' }
]

const conditionOptions = [
  { label: '大于', value: 'GT' },
  { label: '大于等于', value: 'GTE' },
  { label: '小于', value: 'LT' },
  { label: '小于等于', value: 'LTE' },
  { label: '等于', value: 'EQ' },
  { label: '不等于', value: 'NE' },
  { label: '超出范围', value: 'OUT_RANGE' },
  { label: '进入范围', value: 'IN_RANGE' }
]

const pointDataTypeOptions = ['Boolean', 'Int16', 'UInt16', 'Int32', 'UInt32', 'Float32', 'String']

const batchPointJson = ref('[]')
const fullConfigJson = ref('[]')
const fullImportProtocol = ref('MODBUS_TCP')

const selectedProjectId = computed(() => selectedNode.value?.projectId || null)
const selectedGroupId = computed(() => selectedNode.value?.nodeType === 'GROUP' ? selectedNode.value.id : null)
const projectPermissionOf = (projectId) => {
  if (currentUser.value?.roleKey === 'admin') return 'ADMIN'
  const item = (currentUser.value?.projectPermissions || []).find((permission) => permission.projectId === projectId)
  return item?.permissionLevel || 'NONE'
}
const currentProjectPermission = computed(() => projectPermissionOf(selectedProjectId.value))
const canManageProjects = computed(() => currentUser.value?.roleKey === 'admin')
const canManageAssets = computed(() => currentUser.value?.roleKey === 'admin' || currentProjectPermission.value === 'ADMIN')
const currentPermissionLabel = computed(() => {
  if (currentUser.value?.roleKey === 'admin') return '系统管理员'
  if (currentProjectPermission.value === 'ADMIN') return '项目管理'
  if (currentProjectPermission.value === 'OPERATE') return '操作'
  if (currentProjectPermission.value === 'VIEW') return '查看'
  return '无权限'
})
const canOperateCurrentProject = computed(() => {
  return currentUser.value?.roleKey === 'admin' || ['ADMIN', 'OPERATE'].includes(currentProjectPermission.value)
})
const canManageProjectAssets = (projectId) => currentUser.value?.roleKey === 'admin' || projectPermissionOf(projectId) === 'ADMIN'
const canManageTreeNode = (node) => {
  if (!node) return false
  return canManageProjectAssets(node.projectId)
}
const projectPermissionLabel = (projectId) => {
  const permission = projectPermissionOf(projectId)
  if (currentUser.value?.roleKey === 'admin') return '系统管理'
  if (permission === 'ADMIN') return '项目管理'
  if (permission === 'OPERATE') return '操作'
  if (permission === 'VIEW') return '查看'
  return '无权限'
}
const permissionClass = (permission) => {
  if (permission === 'ADMIN') return 'permission-admin'
  if (permission === 'OPERATE') return 'permission-operate'
  if (permission === 'VIEW') return 'permission-view'
  return 'permission-none'
}

const selectedProtocol = computed(() => protocols.value.find((item) => item.protocolType === deviceForm.protocolType) || protocols.value[0] || null)
const selectedProtocolDeviceFields = computed(() => protocolFieldsByTarget('DEVICE'))
const selectedProtocolExtFields = computed(() => protocolFieldsByTarget('EXT_CONFIG'))
const selectedProtocolDeviceBaseFields = computed(() => selectedProtocolDeviceFields.value.filter((field) => !field.advanced))
const selectedProtocolExtBaseFields = computed(() => selectedProtocolExtFields.value.filter((field) => !field.advanced))
const selectedProtocolAdvancedFields = computed(() => [
  ...selectedProtocolDeviceFields.value.filter((field) => field.advanced),
  ...selectedProtocolExtFields.value.filter((field) => field.advanced)
])
const selectedProtocolAdvancedGroups = computed(() => {
  const groups = new Map()
  selectedProtocolAdvancedFields.value.forEach((field) => {
    const name = field.groupName || '高级参数'
    if (!groups.has(name)) {
      groups.set(name, [])
    }
    groups.get(name).push(field)
  })
  return Array.from(groups.entries()).map(([name, fields]) => ({ name, fields }))
})
const isActiveMqtt = computed(() => activeDevice.value?.protocolType === 'MQTT')
const isActiveModbus = computed(() => activeDevice.value?.protocolType === 'MODBUS_TCP')
const isActiveOpcUa = computed(() => activeDevice.value?.protocolType === 'OPC_UA')
const activeProtocol = computed(() => protocols.value.find((item) => item.protocolType === activeDevice.value?.protocolType) || null)
const protocolFieldsByTarget = (target) => {
  const fields = selectedProtocol.value?.fields || []
  return fields.filter((field) => field.target === target)
}
const activeProtocolSupports = (key) => Boolean(activeProtocol.value?.[key])
const protocolOptionLabel = (item) => item?.displayName ? `${item.displayName} / ${item.protocolType}` : item?.protocolType
const pointAddressLabel = computed(() => {
  if (isActiveMqtt.value) return '字段路径'
  if (isActiveOpcUa.value) return 'NodeId'
  return '地址'
})
const pointAddressPlaceholder = computed(() => {
  if (isActiveMqtt.value) return 'MQTT JSON 字段路径，例如 motor.speed'
  if (isActiveOpcUa.value) return 'OPC UA NodeId，例如 ns=2;s=Machine.Temperature'
  return '40001'
})
const activePointTemplateHint = computed(() => {
  const protocol = normalizedProtocolType(activeDevice.value?.protocolType)
  if (protocol === 'MQTT') return '字段 address 填 MQTT Payload 的 JSON 路径，不使用功能码、从站和寄存器长度。'
  if (protocol === 'OPC_UA') return '字段 address 填 OPC UA NodeId，不使用功能码、从站和寄存器长度。'
  return 'Modbus 点位需要地址、功能码、从站、长度和字节序。'
})
const opcTreeProps = {
  children: 'children',
  label: 'label',
  disabled: (data) => !data.variable
}
const fieldSpan = (field) => field?.type === 'textarea' ? 24 : 12
const defaultProtocolType = () => protocols.value[0]?.protocolType || 'MODBUS_TCP'
const normalizedProtocolType = (protocolType) => (protocolType || 'MODBUS_TCP').trim().toUpperCase()

const filteredTree = computed(() => {
  const keyword = treeKeyword.value.trim().toLowerCase()
  if (!keyword) return treeData.value
  const walk = (nodes) => nodes
    .map((node) => ({ ...node, children: walk(node.children || []) }))
    .filter((node) => node.label.toLowerCase().includes(keyword) || node.children.length)
  return walk(treeData.value)
})

const filteredPoints = computed(() => currentPoints.value.filter((point) => pointMatches(point)))
const filteredRuntimePoints = computed(() => runtimePoints.value.filter((item) => {
  const point = item.point || {}
  return pointMatches(point) && (!writeOnly.value || item.writable)
}))
const importPreviewRows = computed(() => {
  const rows = importPreview.value?.rows || []
  if (importPreviewFilter.value === 'SELECTED') return rows.filter((row) => row.selected)
  if (importPreviewFilter.value === 'VALID') return rows.filter((row) => row.valid && !row.duplicate)
  if (importPreviewFilter.value === 'DUPLICATE') return rows.filter((row) => row.duplicate)
  if (importPreviewFilter.value === 'ERROR') return rows.filter((row) => !row.valid)
  return rows
})
const selectedImportCount = computed(() => {
  return (importPreview.value?.rows || []).filter((row) => row.valid && row.selected).length
})
const pointDecimalPlaces = computed(() => alarmRuleTarget.value?.decimalPlaces ?? 2)

const pointMatches = (point) => {
  const keyword = pointKeyword.value.trim().toLowerCase()
  const keywordHit = !keyword
    || (point.pointLabel || '').toLowerCase().includes(keyword)
    || (point.pointKey || '').toLowerCase().includes(keyword)
  const typeHit = !pointTypeFilter.value || point.dataType === pointTypeFilter.value
  const writeHit = !writeOnly.value || isWritable(point)
  return keywordHit && typeHit && writeHit
}

const requiresTwoRegisters = (dataType) => ['Int32', 'UInt32', 'Float32'].includes(dataType)

const handlePointDataTypeChange = (dataType) => {
  pointForm.quantity = requiresTwoRegisters(dataType) ? 2 : 1
  if (dataType === 'Float32' && !pointForm.byteOrder) {
    pointForm.byteOrder = 'CDAB'
  }
}

const canWriteRuntimePoint = (row) => Boolean(row?.writable) && canOperateCurrentProject.value

const denyManage = () => {
  ElMessage.warning('当前账号没有配置管理权限')
}

const denyWrite = () => {
  ElMessage.warning('当前账号没有写入权限')
}

const decorateTree = (nodes) => nodes.map((node) => ({
  ...node,
  treeKey: `${node.nodeType}-${node.id}`,
  children: decorateTree(node.children || [])
}))

const loadAll = async () => {
  await loadTree()
  await loadDevices()
}

const loadTree = async () => {
  try {
    const [rawTree, projectList, groupList, protocolList] = await Promise.all([
      api.getProjectTree({ userId: currentUser.value?.userId }),
      api.listProjects(),
      api.listProjectGroups(),
      api.listProtocols()
    ])
    treeData.value = decorateTree(rawTree || [])
    projects.value = projectList || []
    groups.value = groupList || []
    protocols.value = protocolList || []
    emit('api-state', true)
    if (!selectedNode.value && treeData.value.length) {
      selectedNode.value = treeData.value[0]
      await nextTick()
      treeRef.value?.setCurrentKey(selectedNode.value.treeKey)
    }
  } catch (error) {
    emit('api-state', false)
    ElMessage.error(error.message)
  }
}

const selectTreeNode = async (node) => {
  selectedNode.value = node
  activeDevice.value = null
  currentPoints.value = []
  runtimePoints.value = []
  await loadDevices()
}

const deviceQueryParams = () => ({
  keyword: filters.keyword || undefined,
  protocolType: filters.protocolType || undefined,
  projectId: selectedProjectId.value || undefined,
  groupId: selectedGroupId.value || undefined
})

const loadDevices = async (options = {}) => {
  const preserveActive = Boolean(options.preserveActive)
  const silent = Boolean(options.silent)
  if (!silent) loading.value = true
  try {
    const previousActiveId = activeDevice.value?.id
    const nextDevices = await api.listDevices(deviceQueryParams())
    devices.value = nextDevices || []
    if (devices.value.length) {
      const current = preserveActive && previousActiveId
        ? devices.value.find((item) => item.id === previousActiveId)
        : null
      if (current) {
        activeDevice.value = current
      } else if (!silent) {
        await selectDevice(devices.value[0])
      }
    } else {
      activeDevice.value = null
    }
    emit('api-state', true)
  } catch (error) {
    emit('api-state', false)
    if (!silent) ElMessage.error(error.message)
  } finally {
    if (!silent) loading.value = false
  }
}

const refreshDeviceStatuses = async () => {
  if (deviceDialogVisible.value || fullImportVisible.value) return
  await loadDevices({ preserveActive: true, silent: true })
}

const selectDevice = async (row) => {
  activeDevice.value = row
  currentPoints.value = await api.listPoints({ deviceId: row.id })
}

const refreshPointDrawer = async () => {
  if (!activeDevice.value) return
  const [points, runtime] = await Promise.all([
    api.listPoints({ deviceId: activeDevice.value.id }),
    api.listPointRuntime({ deviceId: activeDevice.value.id })
  ])
  currentPoints.value = points || []
  runtimePoints.value = runtime || []
  selectedPointRows.value = []
}

const startRuntimeRefresh = () => {
  stopRuntimeRefresh()
  runtimeRefreshTimer = window.setInterval(() => {
    if (pointDrawerVisible.value && activePointTab.value === 'runtime') {
      refreshPointDrawer()
    }
  }, 1000)
}

const stopRuntimeRefresh = () => {
  if (runtimeRefreshTimer) {
    window.clearInterval(runtimeRefreshTimer)
    runtimeRefreshTimer = null
  }
}

const startDeviceRefresh = () => {
  stopDeviceRefresh()
  deviceRefreshTimer = window.setInterval(refreshDeviceStatuses, 2000)
}

const stopDeviceRefresh = () => {
  if (deviceRefreshTimer) {
    window.clearInterval(deviceRefreshTimer)
    deviceRefreshTimer = null
  }
}

const handleTreeCommand = async (command, node) => {
  if (!canManageTreeNode(node)) {
    denyManage()
    return
  }
  if (command === 'edit') {
    if (node.nodeType === 'PROJECT') openProjectDialog(projects.value.find((item) => item.id === node.id))
    else openGroupDialog(groups.value.find((item) => item.id === node.id))
    return
  }
  if (command === 'workOrderPolicy') {
    await openWorkOrderPolicy(node)
    return
  }
  if (node.nodeType === 'PROJECT') await removeProject(node)
  else await removeGroup(node)
}

const openProjectDialog = (row) => {
  if (row && !canManageProjectAssets(row.id)) {
    denyManage()
    return
  }
  if (!row && !canManageProjects.value) {
    denyManage()
    return
  }
  resetObject(projectForm)
  Object.assign(projectForm, row || { status: 'ACTIVE' })
  projectDialogVisible.value = true
}

const saveProject = async () => {
  if (projectForm.id && !canManageProjectAssets(projectForm.id)) {
    denyManage()
    return
  }
  if (!projectForm.id && !canManageProjects.value) {
    denyManage()
    return
  }
  if (projectForm.id) await api.updateProject(projectForm.id, projectForm)
  else await api.createProject(projectForm)
  projectDialogVisible.value = false
  ElMessage.success('项目已保存')
  selectedNode.value = null
  await loadAll()
}

const openWorkOrderPolicy = async (node) => {
  if (node.nodeType !== 'PROJECT') return
  if (!canManageProjectAssets(node.projectId)) {
    denyManage()
    return
  }
  const policy = await api.getWorkOrderPolicy(node.projectId)
  workOrderPolicyProjectId.value = node.projectId
  workOrderPolicyProjectName.value = node.label
  Object.assign(workOrderPolicyForm, {
    autoCreateFromAlarm: false,
    allowDispatcherAsAssignee: false,
    allowDispatcherAsVerifier: true,
    allowAssigneeVerifySelf: false,
    autoCloseAfterVerify: true,
    autoArchiveAfterClose: true,
    requireProcessPhoto: false,
    requireFaultReason: true,
    requireProcessMeasure: true
  }, policy)
  workOrderPolicyVisible.value = true
}

const saveWorkOrderPolicy = async () => {
  await api.saveWorkOrderPolicy(workOrderPolicyProjectId.value, workOrderPolicyForm)
  workOrderPolicyVisible.value = false
  ElMessage.success('工单流程设置已保存')
}

const removeProject = async (node) => {
  if (!canManageProjectAssets(node.projectId)) {
    denyManage()
    return
  }
  await ElMessageBox.confirm(`确认删除项目 ${node.label}？项目下分组也会删除，请先确认设备归属。`, '删除项目', { type: 'warning' })
  await api.deleteProject(node.id)
  ElMessage.success('项目已删除')
  selectedNode.value = null
  await loadAll()
}

const openGroupDialog = (row) => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  resetObject(groupForm)
  Object.assign(groupForm, row || {
    projectId: selectedProjectId.value,
    parentId: selectedNode.value?.nodeType === 'GROUP' ? selectedNode.value.id : 0,
    groupType: 'AREA',
    sortNo: 0
  })
  groupDialogVisible.value = true
}

const saveGroup = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  if (groupForm.id) await api.updateProjectGroup(groupForm.id, groupForm)
  else await api.createProjectGroup(groupForm)
  groupDialogVisible.value = false
  ElMessage.success('分组已保存')
  await loadTree()
}

const removeGroup = async (node) => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  await ElMessageBox.confirm(`确认删除分组 ${node.label}？`, '删除分组', { type: 'warning' })
  await api.deleteProjectGroup(node.id)
  ElMessage.success('分组已删除')
  selectedNode.value = null
  await loadAll()
}

const groupsForProject = (projectId, excludeId) => groups.value
  .filter((item) => item.projectId === projectId && item.id !== excludeId)

const parseExtConfig = (text) => {
  if (!text) return {}
  try {
    return JSON.parse(text)
  } catch (e) {
    return {}
  }
}

const applyProtocolDefaults = (force = false) => {
  const fields = selectedProtocol.value?.fields || []
  fields.forEach((field) => {
    if (field.defaultValue === undefined || field.defaultValue === null) return
    if (field.target === 'DEVICE') {
      if (force || deviceForm[field.key] === undefined || deviceForm[field.key] === null || deviceForm[field.key] === '') {
        deviceForm[field.key] = field.defaultValue
      }
    } else if (field.target === 'EXT_CONFIG') {
      if (force || deviceExtConfig[field.key] === undefined || deviceExtConfig[field.key] === null || deviceExtConfig[field.key] === '') {
        deviceExtConfig[field.key] = field.defaultValue
      }
    }
  })
}

const handleProtocolChange = () => {
  resetObject(deviceExtConfig)
  advancedProtocolPanels.value = []
  applyProtocolDefaults(true)
}

const prepareDevicePayload = () => {
  const payload = { ...deviceForm }
  delete payload.status
  const existingExtConfig = parseExtConfig(deviceForm.extConfig)
  payload.extConfig = JSON.stringify({ ...existingExtConfig, ...deviceExtConfig })
  return payload
}

const openDeviceDialog = (row) => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  resetObject(deviceForm)
  resetObject(deviceExtConfig)
  advancedProtocolPanels.value = []
  Object.assign(deviceForm, row || {
    projectId: selectedProjectId.value,
    groupId: selectedGroupId.value || groupsForProject(selectedProjectId.value)[0]?.id,
    deviceType: 'GENERAL',
    protocolType: defaultProtocolType(),
    collectIntervalMs: 1000,
    historyEnabled: true,
    historyMode: 'INHERIT',
    historyIntervalMs: null,
    changeThreshold: null,
    storeOnChange: true,
    status: 'UNKNOWN',
    extConfig: '{}'
  })
  Object.assign(deviceExtConfig, parseExtConfig(deviceForm.extConfig))
  applyProtocolDefaults(!row)
  deviceDialogVisible.value = true
}

const saveDevice = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  if (!deviceForm.historyEnabled) {
    deviceForm.historyMode = 'DISABLED'
  }
  const payload = prepareDevicePayload()
  if (payload.id) await api.updateDevice(payload.id, payload)
  else await api.createDevice(payload)
  deviceDialogVisible.value = false
  ElMessage.success('设备已保存')
  await loadAll()
}

const removeDevice = async (row) => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  await ElMessageBox.confirm(`确认删除设备 ${row.deviceName}？`, '删除设备', { type: 'warning' })
  await api.deleteDevice(row.id)
  ElMessage.success('设备已删除')
  await loadAll()
}

const openPoints = async (row, tabName = 'runtime') => {
  activeDevice.value = row
  activePointTab.value = tabName
  pointDrawerVisible.value = true
  await refreshPointDrawer()
  if (tabName === 'runtime') {
    startRuntimeRefresh()
  }
}

const openPointDialog = (row) => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  resetObject(pointForm)
  Object.assign(pointForm, row || {
    commDeviceId: activeDevice.value?.id,
    functionCode: isActiveModbus.value ? 3 : 0,
    slaveId: 1,
    quantity: 1,
    dataType: isActiveModbus.value ? 'UInt16' : 'Float32',
    byteOrder: 'ABCD',
    wordOrder: 'AB',
    coef: 1,
    decimalPlaces: 2,
    enabled: true,
    accessMode: 'READ_ONLY',
    historyEnabled: true,
    historyMode: 'INTERVAL_CHANGE',
    historyIntervalMs: 300000,
    changeThreshold: null,
    storeOnChange: true
  })
  if (isActiveModbus.value) {
    handlePointDataTypeChange(pointForm.dataType)
  }
  pointDialogVisible.value = true
}

const savePoint = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  if (!isActiveModbus.value) {
    pointForm.functionCode = 0
    pointForm.slaveId = 1
    pointForm.quantity = 1
    pointForm.byteOrder = 'ABCD'
    pointForm.wordOrder = 'AB'
    if (isActiveMqtt.value && !pointForm.address && pointForm.pointKey) {
      pointForm.address = pointForm.pointKey
    }
    if (!pointForm.pointKey && pointForm.address) {
      pointForm.pointKey = pointForm.address
    }
  } else if (requiresTwoRegisters(pointForm.dataType)) {
    pointForm.quantity = 2
  }
  if (!pointForm.historyEnabled) {
    pointForm.historyMode = 'DISABLED'
  }
  pointForm.commDeviceId = activeDevice.value.id
  if (pointForm.id) await api.updatePoint(pointForm.id, pointForm)
  else await api.createPoint(pointForm)
  pointDialogVisible.value = false
  ElMessage.success('点位已保存')
  await refreshPointDrawer()
}

const removePoint = async (row) => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  await ElMessageBox.confirm(`确认删除点位 ${row.pointLabel}？`, '删除点位', { type: 'warning' })
  await api.deletePoint(row.id)
  ElMessage.success('点位已删除')
  await refreshPointDrawer()
}

const handlePointSelectionChange = (rows) => {
  selectedPointRows.value = rows || []
}

const removeSelectedPoints = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  const rows = selectedPointRows.value || []
  if (!rows.length) {
    ElMessage.warning('请选择要删除的点位')
    return
  }
  await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 个点位？删除后对应报警规则也可能失效。`, '批量删除点位', { type: 'warning' })
  const deleted = await api.deletePoints(rows.map((row) => row.id))
  selectedPointRows.value = []
  ElMessage.success(`已删除 ${deleted} 个点位`)
  await refreshPointDrawer()
}

const openAlarmRules = async (row) => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  alarmRuleTarget.value = row
  alarmRuleDialogVisible.value = true
  await loadAlarmRules()
}

const loadAlarmRules = async () => {
  if (!alarmRuleTarget.value?.id) return
  alarmRules.value = await api.listAlarmRules({ pointId: alarmRuleTarget.value.id })
}

const openAlarmRuleForm = (row) => {
  resetObject(alarmRuleForm)
  Object.assign(alarmRuleForm, row || {
    pointId: alarmRuleTarget.value?.id,
    ruleName: `${alarmRuleTarget.value?.pointLabel || '点位'}报警`,
    severity: 'WARN',
    conditionType: 'GT',
    thresholdValue: 0,
    thresholdHigh: null,
    immediateAlarm: false,
    triggerDurationMs: 60000,
    recoverDurationMs: 300000,
    enabled: true,
    remark: ''
  })
  alarmRuleFormVisible.value = true
}

const saveAlarmRule = async () => {
  const payload = { ...alarmRuleForm, pointId: alarmRuleTarget.value?.id }
  payload.recoverValue = null
  payload.recoverHigh = null
  if (!isRangeCondition(payload.conditionType)) {
    payload.thresholdHigh = null
  }
  if (payload.immediateAlarm) {
    payload.triggerDurationMs = 0
  }
  if (payload.id) await api.updateAlarmRule(payload.id, payload)
  else await api.createAlarmRule(payload)
  alarmRuleFormVisible.value = false
  ElMessage.success('报警规则已保存')
  await loadAlarmRules()
}

const removeAlarmRule = async (row) => {
  await ElMessageBox.confirm(`确认删除报警规则 ${row.ruleName}？`, '删除报警规则', { type: 'warning' })
  await api.deleteAlarmRule(row.id)
  ElMessage.success('报警规则已删除')
  await loadAlarmRules()
}

const openBatchPointDialog = () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  if (!activeDevice.value) {
    ElMessage.warning('请先选择设备')
    return
  }
  resetBatchPointTemplate()
  batchPointVisible.value = true
}

const resetBatchPointTemplate = () => {
  batchPointJson.value = JSON.stringify(pointTemplateRows(activeDevice.value?.protocolType), null, 2)
}

const openFullImportDialog = () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  fullImportProtocol.value = normalizedProtocolType(filters.protocolType || activeDevice.value?.protocolType || defaultProtocolType())
  resetFullConfigTemplate()
  fullImportVisible.value = true
}

const resetFullConfigTemplate = () => {
  fullConfigJson.value = JSON.stringify([fullConfigTemplate(fullImportProtocol.value)], null, 2)
}

const fullConfigTemplate = (protocolType) => {
  const protocol = normalizedProtocolType(protocolType)
  const projectId = selectedProjectId.value || projects.value[0]?.id || 1
  const groupId = selectedGroupId.value || groupsForProject(projectId)[0]?.id || 1
  const device = {
    projectId,
    groupId,
    deviceName: templateDeviceName(protocol),
    deviceType: 'PLC',
    protocolType: protocol,
    ipAddress: protocol === 'OPC_UA' ? '127.0.0.1' : '127.0.0.1',
    port: templateDefaultPort(protocol),
    collectIntervalMs: protocol === 'MQTT' ? 1000 : 1000,
    historyEnabled: true,
    historyMode: 'INTERVAL_CHANGE',
    historyIntervalMs: 300000,
    storeOnChange: true,
    extConfig: JSON.stringify(templateExtConfig(protocol)),
    remark: `${templateProtocolName(protocol)} 完整导入示例`
  }
  return {
    device,
    points: pointTemplateRows(protocol)
  }
}

const templateDeviceName = (protocol) => {
  if (protocol === 'MQTT') return 'MQTT 网关设备'
  if (protocol === 'OPC_UA') return 'OPC UA 服务器'
  return 'Modbus TCP 设备'
}

const templateProtocolName = (protocol) => {
  if (protocol === 'MQTT') return 'MQTT'
  if (protocol === 'OPC_UA') return 'OPC UA'
  return 'Modbus TCP'
}

const templateDefaultPort = (protocol) => {
  if (protocol === 'MQTT') return 1883
  if (protocol === 'OPC_UA') return 4840
  return 502
}

const templateExtConfig = (protocol) => {
  if (protocol === 'MQTT') {
    return {
      topic: 'iiot/demo/data',
      publishTopic: 'iiot/demo/write',
      writablePointKeys: 'setpoint.fan_start,setpoint.temperature',
      staleTimeoutMs: 5000,
      cleanSession: true,
      qos: 0
    }
  }
  if (protocol === 'OPC_UA') {
    return {
      securityPolicy: 'None',
      authMode: 'ANONYMOUS',
      endpointUrl: 'opc.tcp://127.0.0.1:4840',
      requestTimeoutMs: 5000
    }
  }
  return {
    slaveId: 1
  }
}

const commonPointOptions = (overrides = {}) => ({
  coef: 1,
  decimalPlaces: 2,
  enabled: true,
  accessMode: 'READ_ONLY',
  historyEnabled: true,
  historyMode: 'INHERIT',
  storeOnChange: true,
  ...overrides
})

const pointTemplateRows = (protocolType) => {
  const protocol = normalizedProtocolType(protocolType)
  if (protocol === 'MQTT') {
    return [
      commonPointOptions({
        pointLabel: '出口温度',
        pointKey: 'temperature',
        address: 'temperature',
        dataType: 'Float32',
        unit: '℃',
        remark: 'MQTT JSON 字段路径：{"temperature": 26.5}'
      }),
      commonPointOptions({
        pointLabel: '电机启动',
        pointKey: 'fan_start',
        address: 'setpoint.fan_start',
        dataType: 'Boolean',
        decimalPlaces: 0,
        accessMode: 'READ_WRITE',
        remark: 'MQTT 可写点位，写入报文由设备 publishTopic / writePayloadTemplate 处理'
      })
    ]
  }
  if (protocol === 'OPC_UA') {
    return [
      commonPointOptions({
        pointLabel: '出口温度',
        pointKey: 'opc_temperature',
        address: 'ns=2;s=Machine.Temperature',
        dataType: 'Float32',
        unit: '℃',
        remark: 'OPC UA 变量 NodeId'
      }),
      commonPointOptions({
        pointLabel: '运行状态',
        pointKey: 'opc_running',
        address: 'ns=2;s=Machine.Running',
        dataType: 'Boolean',
        decimalPlaces: 0,
        accessMode: 'READ_WRITE',
        remark: 'OPC UA 可写变量需服务端节点支持写入'
      })
    ]
  }
  return [
    commonPointOptions({
      pointLabel: '出口温度',
      pointKey: 'outlet_temperature',
      address: '40001',
      functionCode: 3,
      slaveId: 1,
      quantity: 2,
      dataType: 'Float32',
      byteOrder: 'ABCD',
      wordOrder: 'AB',
      coef: 0.1,
      unit: '℃',
      remark: 'Modbus 保持寄存器 40001-40002'
    }),
    commonPointOptions({
      pointLabel: '风机启停',
      pointKey: 'fan_start',
      address: '00001',
      functionCode: 1,
      slaveId: 1,
      quantity: 1,
      dataType: 'Boolean',
      byteOrder: 'ABCD',
      wordOrder: 'AB',
      decimalPlaces: 0,
      unit: '',
      accessMode: 'READ_WRITE',
      remark: 'Modbus 线圈，可写 0/1'
    })
  ]
}

const submitBatchPoints = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  await api.createPoints(activeDevice.value.id, JSON.parse(batchPointJson.value))
  batchPointVisible.value = false
  ElMessage.success('点表已导入')
  await refreshPointDrawer()
}

const downloadPointTemplate = () => {
  const params = new URLSearchParams()
  if (activeDevice.value?.id) params.set('deviceId', activeDevice.value.id)
  if (activeDevice.value?.protocolType) params.set('protocolType', activeDevice.value.protocolType)
  window.open(`/api/points/template?${params.toString()}`, '_blank')
}

const importPointExcel = async (file) => {
  if (!canManageAssets.value) {
    denyManage()
    return false
  }
  if (!activeDevice.value) {
    ElMessage.error('请先选择设备')
    return false
  }
  previewSource.value = 'EXCEL'
  importPreview.value = await api.previewPointImport(activeDevice.value.id, file)
  markPreviewSelection()
  duplicateStrategy.value = importPreview.value.duplicateCount > 0 ? 'SKIP' : 'CREATE'
  importPreviewVisible.value = true
  return false
}

const discoverProtocolPoints = async () => {
  if (isActiveOpcUa.value) {
    await browseOpcUaPoints()
    return
  }
  await discoverMqttPoints()
}

const discoverMqttPoints = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  if (!activeDevice.value) {
    ElMessage.warning('请先选择设备')
    return
  }
  previewSource.value = 'DISCOVERY'
  importPreview.value = await api.previewDiscoveredPoints(activeDevice.value.id)
  markPreviewSelection()
  duplicateStrategy.value = importPreview.value.duplicateCount > 0 ? 'SKIP' : 'CREATE'
  if (!importPreview.value || importPreview.value.totalCount === 0) {
    ElMessage.warning('暂未发现点位，请确认 MQTT Topic 已收到可解析的数据')
    return
  }
  importPreviewVisible.value = true
}

const browseOpcUaPoints = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  if (!activeDevice.value) {
    ElMessage.warning('请先选择设备')
    return
  }
  opcBrowseTree.value = await api.browsePointNodes(activeDevice.value.id)
  selectedOpcNodes.value = []
  if (!opcBrowseTree.value || opcBrowseTree.value.length === 0) {
    ElMessage.warning('未发现 OPC UA 地址空间，请确认设备连接和端点配置')
    return
  }
  opcBrowseVisible.value = true
  await nextTick()
  opcBrowseTreeRef.value?.setCheckedKeys([])
}

const filterOpcNode = () => true

const handleOpcCheck = () => {
  const checked = opcBrowseTreeRef.value?.getCheckedNodes(false, false) || []
  selectedOpcNodes.value = checked.filter((node) => node.variable)
}

const clearOpcSelection = () => {
  opcBrowseTreeRef.value?.setCheckedKeys([])
  selectedOpcNodes.value = []
}

const importSelectedOpcNodes = async () => {
  const nodes = selectedOpcNodes.value
  if (nodes.length === 0) {
    ElMessage.warning('请选择变量节点')
    return
  }
  const rows = nodes.map((node, index) => ({
    rowNumber: index + 1,
    valid: true,
    duplicate: false,
    sampleValue: node.sampleValue,
    errors: [],
    point: {
      commDeviceId: activeDevice.value.id,
      pointLabel: node.label,
      pointKey: node.nodeId,
      address: node.nodeId,
      functionCode: 0,
      slaveId: 1,
      quantity: 1,
      dataType: node.dataType || 'String',
      byteOrder: 'ABCD',
      wordOrder: 'AB',
      coef: 1,
      decimalPlaces: node.sampleValue !== null && typeof node.sampleValue === 'number' ? 2 : 0,
      unit: '',
      enabled: true,
      accessMode: node.writable ? 'READ_WRITE' : 'READ_ONLY',
      historyEnabled: true,
      historyMode: 'INTERVAL_CHANGE',
      historyIntervalMs: 300000,
      storeOnChange: true,
      remark: 'OPC UA 地址空间发现点位'
    }
  }))
  const result = await api.confirmPointImport(activeDevice.value.id, {
    duplicateStrategy: 'SKIP',
    rows
  })
  if (result.failCount > 0) {
    ElMessage.warning(`导入完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`)
    await ElMessageBox.alert(result.errors.join('\n'), '导入失败明细')
  } else {
    ElMessage.success(`导入成功：${result.successCount} 条`)
  }
  opcBrowseVisible.value = false
  await refreshPointDrawer()
}

const markPreviewSelection = () => {
  importPreviewFilter.value = 'ALL'
  resetImportBatch()
  ;(importPreview.value?.rows || []).forEach((row) => {
    row.selected = Boolean(row.valid) && !Boolean(row.duplicate)
    if (row.point && row.point.decimalPlaces == null) {
      row.point.decimalPlaces = 2
    }
  })
}

const setImportSelection = (mode) => {
  ;(importPreview.value?.rows || []).forEach((row) => {
    if (!row.valid) return
    if (mode === 'ALL') row.selected = true
    if (mode === 'NEW_ONLY') row.selected = !row.duplicate
    if (mode === 'NONE') row.selected = false
  })
}

const applyImportBatch = () => {
  const rows = (importPreview.value?.rows || []).filter((row) => row.valid && row.selected && row.point)
  if (rows.length === 0) {
    ElMessage.warning('请先选择有效点位')
    return
  }
  rows.forEach((row) => {
    if (importBatchAccessMode.value) row.point.accessMode = importBatchAccessMode.value
    if (importBatchUnit.value !== '') row.point.unit = importBatchUnit.value
    if (importBatchDecimalPlaces.value !== null && importBatchDecimalPlaces.value !== undefined) {
      row.point.decimalPlaces = importBatchDecimalPlaces.value
    }
  })
  ElMessage.success(`已批量设置 ${rows.length} 个点位`)
}

const resetImportBatch = () => {
  importBatchAccessMode.value = ''
  importBatchUnit.value = ''
  importBatchDecimalPlaces.value = null
}

const confirmPointImport = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  const rows = (importPreview.value?.rows || []).filter((row) => row.valid && row.selected)
  if (rows.length === 0) {
    ElMessage.warning('请选择要导入的点位')
    return
  }
  const result = await api.confirmPointImport(activeDevice.value.id, {
    duplicateStrategy: duplicateStrategy.value,
    rows
  })
  if (result.failCount > 0) {
    ElMessage.warning(`导入完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`)
    await ElMessageBox.alert(result.errors.join('\n'), '导入失败明细')
  } else {
    ElMessage.success(`导入成功：${result.successCount} 条`)
  }
  importPreviewVisible.value = false
  await refreshPointDrawer()
}

const submitFullImport = async () => {
  if (!canManageAssets.value) {
    denyManage()
    return
  }
  await api.createFullConfigs(JSON.parse(fullConfigJson.value))
  fullImportVisible.value = false
  ElMessage.success('完整配置已导入')
  await loadAll()
}

const openWriteDialog = (row) => {
  if (!canWriteRuntimePoint(row)) {
    denyWrite()
    return
  }
  writeTarget.value = row
  writeValue.value = row.point?.dataType === 'Boolean' ? 0 : Number(row.value || 0)
  writeDialogVisible.value = true
}

const submitWritePoint = async () => {
  if (!canWriteRuntimePoint(writeTarget.value)) {
    denyWrite()
    return
  }
  await api.writePoint(writeTarget.value.point.id, writeValue.value)
  writeDialogVisible.value = false
  ElMessage.success('写入请求已发送')
  await refreshPointDrawer()
}

const resetObject = (target) => {
  Object.keys(target).forEach((key) => delete target[key])
}

const isWritable = (point) => point.accessMode === 'READ_WRITE'
const isRangeCondition = (type) => ['OUT_RANGE', 'IN_RANGE'].includes(type)
const severityLabel = (value) => severityOptions.find((item) => item.value === value)?.label || value || '-'
const severityType = (value) => {
  if (value === 'CRITICAL' || value === 'MAJOR') return 'danger'
  if (value === 'MINOR' || value === 'WARN') return 'warning'
  return 'info'
}
const conditionLabel = (value) => conditionOptions.find((item) => item.value === value)?.label || value || '-'
const conditionText = (row) => {
  if (isRangeCondition(row.conditionType)) {
    return `${conditionLabel(row.conditionType)} ${row.thresholdValue ?? '-'} ~ ${row.thresholdHigh ?? '-'}`
  }
  return `${conditionLabel(row.conditionType)} ${row.thresholdValue ?? '-'}`
}
const msLabel = (value) => {
  const ms = Number(value || 0)
  if (ms >= 60000 && ms % 60000 === 0) return `${ms / 60000}分钟`
  if (ms >= 1000 && ms % 1000 === 0) return `${ms / 1000}秒`
  return `${ms}毫秒`
}
const hasPointCollectAlarm = (row) => Number(row?.activePointAlarmCount || 0) > 0
const statusClass = (status) => status === 'ONLINE' ? 'ok' : status === 'OFFLINE' ? 'bad' : 'warn'
const statusLabel = (status) => {
  if (status === 'ONLINE') return '在线'
  if (status === 'OFFLINE') return '离线'
  return '未知'
}
const intervalLabel = (value) => `${Math.round((value || 1000) / 1000)}秒`
const formatRuntimeValue = (row) => {
  const value = row?.value
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'number') {
    const decimalPlaces = row?.point?.decimalPlaces ?? 2
    return value.toFixed(decimalPlaces)
  }
  return value
}
const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'
const groupTypeClass = (type) => {
  if (type === 'LINE') return 'group-line'
  if (type === 'STATION') return 'group-station'
  if (type === 'SYSTEM') return 'group-system'
  return 'group-area'
}
const groupTypeShort = (type) => {
  if (type === 'LINE') return '线'
  if (type === 'STATION') return '站'
  if (type === 'SYSTEM') return '系'
  return '区'
}
const groupTypeLabel = (type) => {
  if (type === 'LINE') return '产线'
  if (type === 'STATION') return '站点'
  if (type === 'SYSTEM') return '系统'
  return '区域'
}

const updateCurrentUser = (event) => {
  currentUser.value = event.detail
  loadAll()
}

onMounted(() => {
  window.addEventListener('iiot-current-user', updateCurrentUser)
  loadAll()
  startDeviceRefresh()
})

watch(pointDrawerVisible, (visible) => {
  if (visible && activePointTab.value === 'runtime') {
    startRuntimeRefresh()
  } else {
    stopRuntimeRefresh()
  }
})

watch(activePointTab, (tabName) => {
  if (pointDrawerVisible.value && tabName === 'runtime') {
    startRuntimeRefresh()
  } else {
    stopRuntimeRefresh()
  }
})

onUnmounted(() => {
  stopRuntimeRefresh()
  stopDeviceRefresh()
  window.removeEventListener('iiot-current-user', updateCurrentUser)
})
</script>

<style scoped>
.asset-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
}

.summary-item {
  height: 72px;
  padding: 14px 16px;
}

.summary-item span {
  display: block;
  color: #667085;
  font-size: 13px;
}

.summary-item strong {
  display: block;
  margin-top: 6px;
  color: #111827;
  font-size: 26px;
  line-height: 1;
}

.workbench {
  min-height: 0;
  flex: 1;
  display: flex;
  gap: 12px;
}

.tree-panel,
.main-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tree-panel {
  width: 300px;
  min-width: 240px;
  max-width: 520px;
  padding: 14px;
  gap: 12px;
  resize: horizontal;
  flex: 0 0 auto;
}

.tree-panel.collapsed {
  width: 58px;
  min-width: 58px;
  padding: 12px 8px;
  resize: none;
}

.main-panel {
  flex: 1;
}

.tree-head,
.tree-actions,
.filter-row,
.panel-breadcrumb,
.table-toolbar,
.point-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-head,
.panel-breadcrumb,
.table-toolbar,
.point-head {
  justify-content: space-between;
}

.tree-head h2,
.point-head h2 {
  margin: 0;
  font-size: 18px;
}

.tree-head span,
.point-head span {
  display: block;
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.asset-tree {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.tree-node {
  width: 100%;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto auto 24px;
  align-items: center;
  gap: 7px;
  min-height: 28px;
}

.tree-node.project-node {
  grid-template-columns: 22px minmax(0, 1fr) auto 0 24px;
}

.tree-node :deep(.el-dropdown) {
  width: 24px;
  display: flex;
  justify-content: center;
}

.node-icon {
  color: #667085;
}

.project-icon {
  color: #475467;
}

.group-kind-icon {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid currentColor;
  background: #fff;
}

.group-area .group-kind-icon,
.group-kind-icon.group-area {
  color: #2563eb;
  background: #eff6ff;
}

.group-line .group-kind-icon,
.group-kind-icon.group-line {
  color: #16a34a;
  background: #f0fdf4;
}

.group-station .group-kind-icon,
.group-kind-icon.group-station {
  color: #d97706;
  background: #fffbeb;
}

.group-system .group-kind-icon,
.group-kind-icon.group-system {
  color: #7c3aed;
  background: #f5f3ff;
}

.tree-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-type {
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  color: #667085;
  background: #f2f4f7;
  font-size: 12px;
  flex: 0 0 auto;
}

.permission-badge.permission-admin {
  color: #2563eb;
  background: #eff6ff;
}

.permission-badge.permission-operate {
  color: #d97706;
  background: #fffbeb;
}

.permission-badge.permission-view {
  color: #475467;
  background: #f2f4f7;
}

.permission-badge.permission-none {
  color: #98a2b3;
  background: #f8fafc;
}

.tree-count {
  min-width: 22px;
  height: 20px;
  padding: 0 7px;
  border-radius: 10px;
  background: #e8eef6;
  color: #344054;
  font-size: 12px;
  display: inline-flex;
  justify-content: center;
  align-items: center;
}

.policy-title {
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #f8fafc;
}

.policy-title strong {
  display: block;
  color: #111827;
  font-size: 16px;
}

.policy-title span {
  display: block;
  margin-top: 6px;
  color: #667085;
  font-size: 13px;
}

.policy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.policy-item {
  min-height: 96px;
  padding: 14px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto 1fr;
  gap: 4px 12px;
  align-items: start;
}

.policy-item strong {
  color: #111827;
}

.policy-item span {
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

.policy-item .el-switch {
  grid-row: 1 / span 2;
  grid-column: 2;
  align-self: center;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

:deep(.action-column .cell) {
  overflow: visible;
}

.panel-breadcrumb {
  height: 50px;
  padding: 0 16px;
  border-bottom: 1px solid #d8e0ea;
}

.permission {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #344054;
  font-size: 13px;
}

.table-toolbar {
  min-height: 56px;
  padding: 10px 16px;
  background: #3f4448;
}

.filter-row {
  flex-wrap: wrap;
}

.status-text {
  margin-left: 8px;
}

.status-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.point-drawer {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.point-head {
  min-height: 72px;
  padding: 4px 4px 14px;
  border-bottom: 1px solid #d8e0ea;
}

.point-tabs {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.point-tabs :deep(.el-tabs__content) {
  min-height: 0;
  flex: 1;
}

.point-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.compact-table {
  border: 1px solid #e4e9f0;
  border-top: none;
}

.device-table :deep(.el-table__cell) {
  padding: 9px 0;
}

.device-table :deep(.cell) {
  padding: 0 12px;
}

.runtime-value {
  margin-right: 6px;
  color: #111827;
}

.form-tip {
  margin-left: 8px;
  color: #667085;
  font-size: 12px;
}

.import-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.import-preview-head,
.import-preview-tools,
.import-batch-panel {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.import-batch-panel {
  padding: 10px 12px;
  justify-content: flex-start;
  background: #f8fafc;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
}

.batch-title {
  color: #475467;
  font-size: 13px;
  font-weight: 600;
}

.import-preview-table :deep(.el-input-number .el-input__inner) {
  text-align: center;
}

.json-template-head {
  margin-bottom: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  background: #f8fafc;
}

.json-template-head strong {
  display: block;
  color: #111827;
  font-size: 14px;
}

.json-template-head span {
  display: block;
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

.json-template-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.import-footer-info {
  float: left;
  line-height: 32px;
  color: #667085;
  font-size: 13px;
}

.alarm-rule-main {
  min-width: 0;
}

.alarm-rule-toolbar {
  min-height: 42px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.alarm-rule-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #111827;
  font-size: 13px;
}

.alarm-rule-title strong {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
}

.alarm-rule-title span {
  color: #667085;
}

.alarm-table :deep(.el-table__empty-block) {
  min-height: 260px;
}

.advanced-protocol {
  margin: 2px 0 8px;
  padding: 0 12px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  background: #f8fafc;
}

.advanced-protocol :deep(.el-collapse-item__header) {
  height: 42px;
  background: transparent;
  color: #475467;
  font-weight: 600;
}

.advanced-protocol :deep(.el-collapse-item__wrap) {
  background: transparent;
}

.device-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow: auto;
}

.device-form :deep(.el-form-item__label) {
  justify-content: flex-end;
  padding-right: 12px;
  white-space: nowrap;
  word-break: keep-all;
}

.device-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.advanced-protocol :deep(.el-form-item__label) {
  color: #475467;
}

.advanced-field-group {
  padding: 10px 0 4px;
  border-top: 1px solid #e6edf5;
}

.advanced-field-group:first-child {
  border-top: 0;
  padding-top: 0;
}

.advanced-field-group-title {
  margin: 0 0 10px;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
}

.opc-browse-layout {
  height: 560px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 12px;
}

.opc-tree-panel,
.opc-selected-panel {
  min-height: 0;
  border: 1px solid #d8e0ea;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.opc-panel-head {
  height: 46px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e9f0;
}

.opc-panel-head strong {
  color: #111827;
}

.opc-panel-head span {
  color: #667085;
  font-size: 12px;
}

.opc-tree-panel .el-tree {
  height: calc(100% - 46px);
  overflow: auto;
  padding: 8px;
}

.opc-node-row {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.opc-node-name.variable {
  color: #111827;
  font-weight: 600;
}

.opc-node-id {
  color: #98a2b3;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.opc-selected-list {
  height: calc(100% - 46px);
  overflow: auto;
  padding: 10px;
}

.opc-selected-item {
  padding: 10px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  margin-bottom: 8px;
  background: #f8fafc;
}

.opc-selected-item strong,
.opc-selected-item span,
.opc-selected-item em {
  display: block;
}

.opc-selected-item span {
  margin-top: 4px;
  color: #667085;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  word-break: break-all;
}

.opc-selected-item em {
  margin-top: 6px;
  color: #2563eb;
  font-style: normal;
  font-weight: 700;
}

.opc-empty {
  height: 100%;
  display: grid;
  place-items: center;
  color: #98a2b3;
}

@media (max-width: 1180px) {
  .summary-row {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }

  .tree-panel {
    width: 260px;
  }

}
</style>
