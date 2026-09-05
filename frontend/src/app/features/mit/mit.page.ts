import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckoutApiService } from '../../core/api/checkout-api.service';
import { OperationsApiService, PaymentInstrument } from '../../core/api/operations-api.service';
import { PaymentSubmission } from '../../core/api/checkout-api.service';

/** Individual reusable-method charging and Koza monthly-batch workspace. */
@Component({ changeDetection: ChangeDetectionStrategy.OnPush, imports:[FormsModule], selector: 'app-mit-page', styleUrl: './mit.page.scss', templateUrl: './mit.page.html' })
export class MitPage implements OnInit {
  private readonly api=inject(OperationsApiService);
  private readonly checkoutApi=inject(CheckoutApiService);
  protected readonly section=signal<'individual'|'koza'>('individual');
  protected readonly instruments=signal<PaymentInstrument[]>([]);
  protected readonly selectedId=signal('');
  protected readonly result=signal<PaymentSubmission|null>(null);
  protected readonly message=signal<string|null>(null);
  protected readonly submitting=signal(false);
  protected readonly operatorTokenRequired=signal(true);
  protected amount=10000;protected execution='CAPTURE';protected reference=`SUPPORT-${new Date().toISOString().slice(0,10).replaceAll('-','')}-001`;protected operatorToken='';
  protected primaryId='';protected backupId='';
  protected readonly selectedKoza=signal<Set<string>>(new Set());
  protected batchReference=`KOZA-${new Date().toISOString().slice(0,7).replace('-','')}-001`;
  protected readonly selected=computed(()=>this.instruments().find(item=>item.instrumentId===this.selectedId())??null);
  protected readonly customers=computed(()=>[...new Set(this.instruments().map(item=>item.customerCode))]);
  ngOnInit():void{this.checkoutApi.getCheckoutExperience().subscribe(settings=>this.operatorTokenRequired.set(settings.operatorTokenRequired));this.reload();}
  protected choose(id:string):void{this.selectedId.set(id);const item=this.selected();if(item){if(!this.supportsFlexibleExecution(item))this.execution='CAPTURE';const customer=this.instruments().filter(i=>i.customerCode===item.customerCode);this.primaryId=customer.find(i=>i.preferenceRole==='PRIMARY')?.instrumentId??id;this.backupId=customer.find(i=>i.preferenceRole==='BACKUP')?.instrumentId??'';}}
  protected supportsFlexibleExecution(item:PaymentInstrument|null=this.selected()):boolean{return item?.method==='CARD'||item?.method==='PAYPAY';}
  protected customerInstruments():PaymentInstrument[]{const item=this.selected();return item?this.instruments().filter(i=>i.customerCode===item.customerCode):[];}
  protected submit():void{const item=this.selected();if(!item||(this.operatorTokenRequired()&&!this.operatorToken)){this.message.set(this.operatorTokenRequired()?'Select an instrument and enter the operator token.':'Select an instrument.');return;}const mode=this.supportsFlexibleExecution(item)?this.execution:'CAPTURE';const label=mode==='AUTH'?'authorize now and capture later':'run an immediate sale';if(!window.confirm(`Submit JPY ${this.amount.toLocaleString()} using ${item.maskedDisplay} and ${label}?`))return;this.submitting.set(true);this.message.set(null);this.api.submitMit(item.instrumentId,this.amount,this.reference,mode,this.operatorToken,crypto.randomUUID()).subscribe({next:result=>{this.result.set(result);this.submitting.set(false);this.message.set(`${result.state} · ${result.transactionId}`);},error:()=>{this.submitting.set(false);this.message.set('The recurring payment could not be submitted. Check the instrument and transaction thread.');}});}
  protected savePreferences():void{const item=this.selected();if(!item||(this.operatorTokenRequired()&&!this.operatorToken))return;this.api.setPreferences(item.customerCode,this.primaryId,this.backupId||null,this.operatorToken).subscribe({next:()=>{this.message.set('Primary and backup preferences saved.');this.reload();},error:()=>this.message.set('Preferences could not be saved.')});}
  protected toggleKoza(id:string,checked:boolean):void{const selected=new Set(this.selectedKoza());checked?selected.add(id):selected.delete(id);this.selectedKoza.set(selected);}
  protected submitKoza():void{if(!this.selectedKoza().size||(this.operatorTokenRequired()&&!this.operatorToken)){this.message.set(this.operatorTokenRequired()?'Select at least one mandate and enter the operator token.':'Select at least one mandate.');return;}const confirmation=window.prompt(`Type ${this.batchReference} to submit ${this.selectedKoza().size} separate debit request(s).`);if(confirmation!==this.batchReference){this.message.set('Batch submission cancelled.');return;}const items=this.instruments().filter(i=>this.selectedKoza().has(i.instrumentId)).map(i=>({instrumentId:i.instrumentId,amountJpy:10000}));this.submitting.set(true);this.api.submitKozaBatch({batchReference:this.batchReference,cycleYear:2026,cycleMonth:9,targetDate:'27',submissionCutoffAt:'2026-09-15T15:00:00+09:00',expectedResultDate:'2026-09-30',items},this.operatorToken).subscribe({next:result=>{this.submitting.set(false);this.selectedKoza.set(new Set());this.message.set(`${result.submittedCount} requests scheduled · ${result.batchId}`);},error:()=>{this.submitting.set(false);this.message.set('The Koza batch could not be submitted.');}});}
  private reload():void{this.api.instruments().subscribe(items=>{this.instruments.set(items);const primary=items.find(i=>i.preferenceRole==='PRIMARY'&&i.method!=='KOZA_FURIKAE_SELECT')??items.find(i=>i.method!=='KOZA_FURIKAE_SELECT');if(primary)this.choose(primary.instrumentId);});}
}
