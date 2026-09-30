import {test} from '@playwright/test';
import {exerciseRequest} from './sample-request-template-helper';
test('PC template submission survives approval and task lookup',async({page})=>{await exerciseRequest(page,false);});
