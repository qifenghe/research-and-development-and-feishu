import {test} from '@playwright/test';
import {exerciseRequest} from './sample-request-template-helper';
test('Mobile template submission survives approval and task lookup',async({page})=>{await exerciseRequest(page,true);});
