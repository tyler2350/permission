package com.lq.service.impl;

import com.google.common.collect.Lists;
import com.lq.entity.SysAcl;
import com.lq.enums.DataUseful;
import com.lq.enums.ErrorCode;
import com.lq.exception.ParamException;
import com.lq.exception.PermissionException;
import com.lq.mapping.BeanMapper;
import com.lq.model.SysDeptModel;
import com.lq.repository.SysAclRepository;
import com.lq.utils.Const;
import com.lq.utils.LoginHolder;
import com.lq.utils.ParamValidator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import com.lq.entity.SysAclModule;
import com.lq.repository.SysAclModuleRepository;
import com.lq.model.SysAclModuleModel;
import com.lq.service.SysAclModuleService;
import org.springframework.util.CollectionUtils;

import javax.security.auth.login.LoginContext;
import java.util.*;

@Service
public class SysAclModuleServiceImpl implements SysAclModuleService {

    @Autowired
    private BeanMapper beanMapper;

    @Autowired
    private SysAclModuleRepository sysAclModuleRepo;

    @Autowired
    private SysAclRepository aclRepository;

    private Comparator<SysAclModule> comparator = new Comparator<SysAclModule>() {
        @Override
        public int compare(SysAclModule o1, SysAclModule o2) {
            return o1.getSeq() - o2.getSeq();
        }
    };


    @Transactional
    @Override
    public int create(SysAclModuleModel sysAclModuleModel) {
        return sysAclModuleRepo.insertSelective(beanMapper.map(sysAclModuleModel, SysAclModule.class));
    }


    @Transactional
    @Override
    public int createSelective(SysAclModuleModel sysAclModuleModel) {
        ParamValidator.check(sysAclModuleModel);
        checkExist(sysAclModuleModel.getParentId(), sysAclModuleModel.getName());
        sysAclModuleModel.setOperateIp("127.0.0.1");
        sysAclModuleModel.setOperator(LoginHolder.getUser().getUsername());
        sysAclModuleModel.setOperteTime(new Date());
        int res = sysAclModuleRepo.insertSelective(beanMapper.map(sysAclModuleModel, SysAclModule.class));

        //after insert db we can  get  id then update  the level
        SysAclModuleModel sysAclModuleModel2 = new SysAclModuleModel();
        sysAclModuleModel2.setId(sysAclModuleModel.getId());
        sysAclModuleModel2.setLevel(completeLevel(sysAclModuleModel.getParentId(), sysAclModuleModel.getId()));
        sysAclModuleModel2.setOperteTime(new Date());
        res = updateByPrimaryKeySelective(sysAclModuleModel2);
        return res;
    }

    /**
     * 组装层级level   0.1.2.3
     *
     * @param parentId 父id
     * @param id       当前id
     * @return
     */
    private String completeLevel(Integer parentId, Integer id) {
        if (id == null) {
            throw new PermissionException(ErrorCode.PARAM_MISS.getMsg());
        }
        String parentLevel;
        if (parentId == null) {
            parentLevel = String.valueOf(Const.DATA_ROOT);
        } else {
            SysAclModuleModel beforeSysAclModule = findByPrimaryKey(parentId);
            if (beforeSysAclModule == null) {
                throw new PermissionException(ErrorCode.SYSACL_MODULE_NOTEXIST.getMsg());
            }
            parentLevel = beforeSysAclModule.getLevel() + id;
        }
        return parentLevel + Const.SEPARATOR_POINT;
    }


    private void checkExist(Integer parentId, String name) {
        if (StringUtils.isBlank(name)) {
            throw new ParamException(ErrorCode.PARAM_MISS.getMsg());
        }
        if (parentId == null) {
            parentId = Const.DATA_ROOT;
        }
        int res = sysAclModuleRepo.selectAclModuleRepeat(parentId, name);
        if (res > 0) {
            throw new ParamException(ErrorCode.DEPT_REPEAT.getMsg());
        }
    }

    @Transactional
    @Override
    public int deleteByPrimaryKey(Integer id) {
        if (id == null || id < 0) {
            throw new PermissionException(ErrorCode.PARAM_MISS.getMsg());
        }
        //two way to delete it:   1 update the status=-1;  2 delete it really;
        SysAclModuleModel before = findByPrimaryKey(id);
        if (before == null) {
            throw new PermissionException(ErrorCode.SYSACL_MODULE_NOTEXIST.getMsg());
        }
        //to  check  this  sysaclmodule  have more  relation datas
        SysAclModuleModel param1 = new SysAclModuleModel();
        param1.setParentId(id);
        List<SysAclModule> sysAclModules = sysAclModuleRepo.selectPage(beanMapper.map(param1, SysAclModule.class), null);
        if (!CollectionUtils.isEmpty(sysAclModules)) {
            throw new PermissionException(ErrorCode.ACLMODULE_RELATION_ERROR.getMsg());
        }
        SysAcl param3 = new SysAcl();
        param3.setAclModuleId(id);
        int num=aclRepository.selectCount(param3);
        if(num>0){
            throw new PermissionException(ErrorCode.ACLMODULE_RELATION_ERROR.getMsg());
        }
        // 1
        SysAclModuleModel param2 = new SysAclModuleModel();
        param2.setId(id);
        param2.setStatus(DataUseful.NOUSEFUL.getCode());
        return updateByPrimaryKeySelective(param2);
        // 2  return sysAclModuleRepo.deleteByPrimaryKey(id);
    }

    @Transactional(readOnly = true)
    @Override
    public SysAclModuleModel findByPrimaryKey(Integer id) {
        SysAclModule sysAclModule = sysAclModuleRepo.selectByPrimaryKey(id);
        return beanMapper.map(sysAclModule, SysAclModuleModel.class);
    }

    @Transactional(readOnly = true)
    @Override
    public long selectCount(SysAclModuleModel sysAclModuleModel) {
        return sysAclModuleRepo.selectCount(beanMapper.map(sysAclModuleModel, SysAclModule.class));
    }

    @Transactional(readOnly = true)
    @Override
    public List<SysAclModuleModel> selectPage(SysAclModuleModel sysAclModuleModel, Pageable pageable) {
        SysAclModule sysAclModule = beanMapper.map(sysAclModuleModel, SysAclModule.class);
        return beanMapper.mapAsList(sysAclModuleRepo.selectPage(sysAclModule, pageable), SysAclModuleModel.class);
    }

    /**
     * 获取权限模块树
     *
     * @return
     */
    @Override
    public List<SysAclModule> getSysAclModuleTree() {
        List<SysAclModule> allAclModules = sysAclModuleRepo.selectPage(new SysAclModule(), null);
        if (CollectionUtils.isEmpty(allAclModules)) {
            return Lists.newArrayList();
        }
        Iterator<SysAclModule> iterator = allAclModules.iterator();
        List<SysAclModule> rootAclModules = new LinkedList<>();
        while (iterator.hasNext()) {
            SysAclModule next = iterator.next();
            if (next.getParentId() == Const.DATA_ROOT) {
                rootAclModules.add(next);
                iterator.remove();
            }
        }
        if (!rootAclModules.isEmpty()) {
            rootAclModules.sort(comparator);
        }
        if (!allAclModules.isEmpty() && !rootAclModules.isEmpty()) {
            rootAclModules.forEach(e -> constructTree(e, allAclModules));
        }
        return rootAclModules;
    }

    /**
     * 递归构造权限模块树
     *
     * @param parentNode    父节点
     * @param allAclModules 剩余需构造的节点集
     */
    private void constructTree(SysAclModule parentNode, List<SysAclModule> allAclModules) {
        Iterator<SysAclModule> iterator = allAclModules.iterator();
        List<SysAclModule> childrens = new LinkedList<>();
        while (iterator.hasNext()) {
            SysAclModule next = iterator.next();
            if (next.getParentId().equals(parentNode.getId())) {
                childrens.add(next);
                iterator.remove();
            }
        }
        if (!childrens.isEmpty()) {
            childrens.sort(comparator);
        }
        if (!CollectionUtils.isEmpty(childrens)) {
            parentNode.setNodes(childrens);
        }
        if (!allAclModules.isEmpty() && !childrens.isEmpty()) {
            childrens.forEach(e -> constructTree(e, allAclModules));
        }
    }

    @Transactional
    @Override
    public int updateByPrimaryKey(SysAclModuleModel sysAclModuleModel) {
        return sysAclModuleRepo.updateByPrimaryKey(beanMapper.map(sysAclModuleModel, SysAclModule.class));
    }

    @Transactional
    @Override
    public int updateByPrimaryKeySelective(SysAclModuleModel sysAclModuleModel) {
        ParamValidator.check(sysAclModuleModel);
        SysAclModuleModel before = findByPrimaryKey(sysAclModuleModel.getId());
        if (!sysAclModuleModel.getName().equals(before.getName())) {
            checkExist(sysAclModuleModel.getParentId(), sysAclModuleModel.getName());
        }
        sysAclModuleModel.setOperateIp("127.0.0.1");
        sysAclModuleModel.setOperator(LoginHolder.getUser().getUsername());
        sysAclModuleModel.setOperteTime(new Date());
        //these properties can not be modified
        sysAclModuleModel.setId(null);
        sysAclModuleModel.setParentId(null);
        sysAclModuleModel.setLevel(null);
        return sysAclModuleRepo.updateByPrimaryKeySelective(beanMapper.map(sysAclModuleModel, SysAclModule.class));
    }

}
